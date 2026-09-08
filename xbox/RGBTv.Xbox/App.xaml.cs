// RGBTv — Xbox shell application object.
using System;
using System.Diagnostics;
using Windows.ApplicationModel;
using Windows.ApplicationModel.Activation;
using Windows.UI.ViewManagement;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace RGBTv.Xbox
{
    sealed partial class App : Application
    {
        public App()
        {
            InitializeComponent();
            Suspending += OnSuspending;
            // gamepad drives the page directly (no virtual mouse cursor)
            RequiresPointerMode = ApplicationRequiresPointerMode.WhenRequested;
            // media remote / controller media keys are handled natively (SMTC), not by Chromium
            Environment.SetEnvironmentVariable("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", "--disable-features=HardwareMediaKeyHandling --autoplay-policy=no-user-gesture-required");
            Environment.SetEnvironmentVariable("WEBVIEW2_DEFAULT_BACKGROUND_COLOR", "FF0B0F19");
            // real 1920x1080 pixels instead of the 2x XAML scaling Xbox applies by default
            if (!ApplicationViewScaling.TrySetDisableLayoutScaling(true)) Debug.WriteLine("Could not disable layout scaling");
        }

        protected override void OnLaunched(LaunchActivatedEventArgs e)
        {
            Frame rootFrame = Window.Current.Content as Frame;
            if (rootFrame == null)
            {
                rootFrame = new Frame();
                rootFrame.NavigationFailed += (s, a) => throw new Exception("Failed to load page " + a.SourcePageType.FullName);
                Window.Current.Content = rootFrame;
            }
            if (!e.PrelaunchActivated)
            {
                if (rootFrame.Content == null) rootFrame.Navigate(typeof(MainPage), e.Arguments);
                Window.Current.Activate();
            }
        }

        private void OnSuspending(object sender, SuspendingEventArgs e)
        {
            var deferral = e.SuspendingOperation.GetDeferral();
            deferral.Complete();
        }
    }
}
