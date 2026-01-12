import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    // Initialize our deep link handler
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Handle the deep link
                    let urlString = url.absoluteString
                    DeepLinkHandler.Companion().getInstance().handleResetPasswordDeepLink(uri: urlString)
                }
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Handle deep link if app was launched from URL
        if let url = launchOptions?[UIApplication.LaunchOptionsKey.url] as? URL {
            let urlString = url.absoluteString
            DeepLinkHandler.Companion().getInstance().handleResetPasswordDeepLink(uri: urlString)
        }
        return true
    }
}
