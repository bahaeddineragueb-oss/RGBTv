// RGBTv — Xbox shell (UWP + WebView2). Hosts the same HTML5 app as the webOS / Android / Windows builds.
//  * www/  (copied from ../../app by sync.js) is served through a virtual host name (https://app.rgbtv/)
//  * all IPTV API calls from JS go through the native HttpClient (custom headers, no CORS, player User-Agent)
//  * gamepad input reaches the page as key events (WebView2 on Xbox); B / BackRequested is forwarded as BACK
//  * SystemMediaTransportControls are kept in sync so the console keeps the screen on while a stream plays
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Windows.Data.Json;
using Windows.Media;
using Windows.System.Display;
using Windows.System.Profile;
using Windows.UI;
using Windows.UI.Core;
using Windows.UI.ViewManagement;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Media;

namespace RGBTv.Xbox
{
    public sealed partial class MainPage : Page
    {
        private const string HostName = "app.rgbtv";
        private const string StartUri = "https://" + HostName + "/index.html";
        private const string PlayerUserAgent = "RGBTv/2.1 (Xbox) IPTVSmarters/3.1 ExoPlayerLib/2.18";

        private WebView2 webView;
        private bool ready = false;
        private readonly SystemMediaTransportControls smtc;
        private readonly DisplayRequest displayRequest = new DisplayRequest();
        private bool displayHeld = false;
        private static readonly HttpClient http = MakeHttp();

        private static HttpClient MakeHttp()
        {
            var h = new HttpClientHandler { AllowAutoRedirect = true, UseCookies = false };
            try { h.ServerCertificateCustomValidationCallback = (m, c, ch, e) => true; } catch { }
            var c = new HttpClient(h) { Timeout = TimeSpan.FromSeconds(60) };
            return c;
        }

        public MainPage()
        {
            InitializeComponent();
            // draw edge to edge (the app has its own TV-safe margins)
            ApplicationView.GetForCurrentView().SetDesiredBoundsMode(ApplicationViewBoundsMode.UseCoreWindow);

            smtc = SystemMediaTransportControls.GetForCurrentView();
            smtc.IsPlayEnabled = true; smtc.IsPauseEnabled = true; smtc.IsStopEnabled = true; smtc.IsNextEnabled = true; smtc.IsPreviousEnabled = true;
            smtc.ButtonPressed += OnSmtcButton;

            SystemNavigationManager.GetForCurrentView().BackRequested += OnBackRequested;
            InitWebView();
        }

        private async void InitWebView()
        {
            webView = new WebView2 { Background = new SolidColorBrush(Color.FromArgb(255, 11, 15, 25)) };
            try { await webView.EnsureCoreWebView2Async(); }
            catch (Exception ex) { ShowError("WebView2 runtime error: " + ex.Message); return; }
            var core = webView.CoreWebView2;
            if (core == null) { ShowError("WebView2 unavailable"); return; }

            Content = webView;
            webView.Focus(FocusState.Programmatic);

            core.Settings.AreDefaultContextMenusEnabled = false;
            core.Settings.IsGeneralAutofillEnabled = false;
            core.Settings.IsPasswordAutosaveEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.IsZoomControlEnabled = false;
            core.Settings.IsReputationCheckingRequired = false;
            core.Settings.AreDevToolsEnabled = false;

            core.SetVirtualHostNameToFolderMapping(HostName, "www", CoreWebView2HostResourceAccessKind.Allow);

            // tell the page it runs inside the Xbox shell before any of its scripts execute
            string device = "Xbox";
            try { device = AnalyticsInfo.DeviceForm; } catch { }
            await core.AddScriptToExecuteOnDocumentCreatedAsync("window.__RGBTV_XBOX={device:" + JsonValue.CreateStringValue(device).Stringify() + "};");

            core.WebMessageReceived += OnWebMessage;
            core.ProcessFailed += (s, a) => { Debug.WriteLine("WebView2 process failed: " + a.Reason); if (a.ProcessFailedKind == CoreWebView2ProcessFailedKind.BrowserProcessExited) InitWebView(); };
            webView.NavigationCompleted += (s, a) => { ready = a.IsSuccess; if (!a.IsSuccess) ShowError("Failed to load app: " + a.WebErrorStatus); };
            webView.Source = new Uri(StartUri);
        }

        private void ShowError(string msg)
        {
            Content = new TextBlock { Text = msg, Foreground = new SolidColorBrush(Colors.White), FontSize = 32, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(80) };
        }

        private async Task Js(string script)
        {
            if (!ready || webView == null) return;
            try { await webView.ExecuteScriptAsync(script); } catch (Exception ex) { Debug.WriteLine("JS error: " + ex.Message); }
        }

        /* ---------- messages from xbox.js ---------- */
        private async void OnWebMessage(CoreWebView2 sender, CoreWebView2WebMessageReceivedEventArgs args)
        {
            JsonObject msg;
            if (!JsonObject.TryParse(args.TryGetWebMessageAsString(), out msg)) return;
            string type = msg.GetNamedString("type", "");
            switch (type)
            {
                case "fetch": _ = DoFetch(msg); break;
                case "exit": Application.Current.Exit(); break;
                case "playback": UpdatePlayback(msg); break;
            }
            await Task.CompletedTask;
        }

        private async Task DoFetch(JsonObject m)
        {
            int id = (int)m.GetNamedNumber("id", 0);
            string url = m.GetNamedString("url", ""), method = m.GetNamedString("method", "GET"), body = m.GetNamedString("body", "");
            int timeout = (int)m.GetNamedNumber("timeout", 20000);
            var result = new JsonObject();
            try
            {
                using (var cts = new CancellationTokenSource(timeout))
                using (var req = new HttpRequestMessage(new HttpMethod(string.IsNullOrEmpty(method) ? "GET" : method), url))
                {
                    req.Headers.TryAddWithoutValidation("User-Agent", PlayerUserAgent);
                    req.Headers.TryAddWithoutValidation("Accept", "*/*");
                    JsonObject headers = m.GetNamedObject("headers", new JsonObject());
                    string contentType = null;
                    foreach (var kv in headers)
                    {
                        string v = kv.Value.ValueType == JsonValueType.String ? kv.Value.GetString() : kv.Value.Stringify();
                        if (kv.Key.Equals("Content-Type", StringComparison.OrdinalIgnoreCase)) { contentType = v; continue; }
                        req.Headers.TryAddWithoutValidation(kv.Key, v);
                    }
                    if (!string.IsNullOrEmpty(body))
                    {
                        req.Content = new StringContent(body, Encoding.UTF8);
                        if (contentType != null) req.Content.Headers.TryAddWithoutValidation("Content-Type", contentType);
                    }
                    using (var res = await http.SendAsync(req, HttpCompletionOption.ResponseContentRead, cts.Token))
                    {
                        string txt = await res.Content.ReadAsStringAsync();
                        result["status"] = JsonValue.CreateNumberValue((int)res.StatusCode);
                        result["body"] = JsonValue.CreateStringValue(txt);
                    }
                }
            }
            catch (TaskCanceledException) { result["status"] = JsonValue.CreateNumberValue(0); result["error"] = JsonValue.CreateStringValue("timed out"); }
            catch (Exception ex) { result["status"] = JsonValue.CreateNumberValue(0); result["error"] = JsonValue.CreateStringValue(ex.Message); }

            string js = "window.RGBTvHostCb&&RGBTvHostCb(" + id + "," + JsonValue.CreateStringValue(result.Stringify()).Stringify() + ")";
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, async () => await Js(js));
        }

        /* ---------- playback state → SMTC + display request ---------- */
        private void UpdatePlayback(JsonObject m)
        {
            string state = m.GetNamedString("state", "stopped");
            var upd = smtc.DisplayUpdater;
            upd.Type = MediaPlaybackType.Video;
            upd.VideoProperties.Title = m.GetNamedString("title", "RGBTv");
            upd.VideoProperties.Subtitle = m.GetNamedString("subtitle", "");
            upd.Update();
            smtc.PlaybackStatus = state == "playing" ? MediaPlaybackStatus.Playing : state == "paused" ? MediaPlaybackStatus.Paused : MediaPlaybackStatus.Stopped;
            bool hold = state == "playing";
            try { if (hold && !displayHeld) { displayRequest.RequestActive(); displayHeld = true; } else if (!hold && displayHeld) { displayRequest.RequestRelease(); displayHeld = false; } } catch { }
        }

        private async void OnSmtcButton(SystemMediaTransportControls s, SystemMediaTransportControlsButtonPressedEventArgs a)
        {
            int code = 0;
            switch (a.Button)
            {
                case SystemMediaTransportControlsButton.Play: code = 415; break;
                case SystemMediaTransportControlsButton.Pause: code = 19; break;
                case SystemMediaTransportControlsButton.Stop: code = 413; break;
                case SystemMediaTransportControlsButton.Next: code = 418; break;
                case SystemMediaTransportControlsButton.Previous: code = 419; break;
                case SystemMediaTransportControlsButton.FastForward: code = 417; break;
                case SystemMediaTransportControlsButton.Rewind: code = 412; break;
            }
            if (code == 0) return;
            await Dispatcher.RunAsync(CoreDispatcherPriority.Normal, async () => await Js("window.Nav&&Nav.press(" + code + ")"));
        }

        /* B button / system back: let the web app decide (it exits itself from the top level via RGBTvHost.exit) */
        private async void OnBackRequested(object sender, BackRequestedEventArgs e)
        {
            e.Handled = true;
            await Js("window.RGBTvXbox&&RGBTvXbox.back()");
        }
    }
}
