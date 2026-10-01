import SwiftUI
import FirebaseCore
import GoogleSignIn
import WidgetKit
import crossPlatform

class iOSWidgetPlatformDelegate: NSObject, WidgetPlatformDelegate {
    func reloadAll() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    func getWidgetIds(callback: @escaping ([KotlinInt]) -> Void) {
        WidgetCenter.shared.getCurrentConfigurations { result in
            switch result {
            case .success(let widgetInfo):
                let ids = (0..<widgetInfo.count).map { KotlinInt(value: Int32($0)) }
                callback(ids)
            case .failure:
                callback([])
            }
        }
    }

    func getWidgetSize(widgetId: Int32, callback: @escaping (String) -> Void) {
        WidgetCenter.shared.getCurrentConfigurations { result in
            switch result {
            case .success(let widgetInfo):
                let index = Int(widgetId)
                if index >= 0 && index < widgetInfo.count {
                    let family = widgetInfo[index].family
                    switch family {
                    case .systemSmall: callback("small")
                    case .systemMedium: callback("medium")
                    case .systemLarge: callback("large")
                    case .systemExtraLarge: callback("extraLarge")
                    case .accessoryCircular: callback("circular")
                    case .accessoryRectangular: callback("rectangular")
                    case .accessoryInline: callback("inline")
                    @unknown default: callback("unknown")
                    }
                } else {
                    callback("UNKNOWN")
                }
            case .failure:
                callback("UNKNOWN")
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let _ = crossPlatform.HSKAppServices.shared
        return crossPlatform.AppViewControllerKt.AppViewController() // Call the Kotlin ViewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

class AppDelegate: NSObject, UIApplicationDelegate {
  func application(_ application: UIApplication,
                   didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
    FirebaseApp.configure()

    // Attempt to get the Client ID from Firebase options first, then fallback to Info.plist
    if let clientID = FirebaseApp.app()?.options.clientID ?? Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
    }

    // Register Widget Delegate
    WidgetProvider.shared.delegate = iOSWidgetPlatformDelegate()

    return true
  }
}

@main
struct hskwidgetApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    handleOpenURL(url)
                }
        }
    }
    
    private func handleOpenURL(_ url: URL) {
        var appIntent: crossPlatform.AppIntent? = nil
        
        if url.scheme == "hskwidget" {
            let components = URLComponents(url: url, resolvingAgainstBaseURL: true)
            let source: crossPlatform.IntentSources
            switch components?.queryItems?.first(where: { $0.name == "src" })?.value {
            case "share":
                source = crossPlatform.IntentSources.share
            case "widget":
                source = crossPlatform.IntentSources.widget
            default:
                source = crossPlatform.IntentSources.inApp
            }
            
            if url.host == "search" {
                if let word = components?.queryItems?.first(where: { $0.name == "q" })?.value {
                    let searchQuery = crossPlatform.SearchQuery.companion.fromString(query: word)
                    appIntent = crossPlatform.AppIntent.Search(query: searchQuery, source: source)
                }
            } else if url.host == "searchTTS" {
                if let word = components?.queryItems?.first(where: { $0.name == "q" })?.value {
                    let searchQuery = crossPlatform.SearchQuery.companion.fromString(query: word)
                    appIntent = crossPlatform.AppIntent.SearchTTS(query: searchQuery, source: source)
                }
            } else if url.host == "ocr" {
                if let path = components?.queryItems?.first(where: { $0.name == "path" })?.value {
                    appIntent = crossPlatform.AppIntent.ImageOCR(path: path, source: source)
                } else {
                    appIntent = crossPlatform.AppIntent.OCRCapture()
                }
            } else if url.host == "configure" {
                appIntent = crossPlatform.AppIntent.WidgetConfiguration(widgetId: 0)
            }
        }

        if let intent = appIntent {
            crossPlatform.AppIntentBus.shared.emit(intent: intent)
        }
    }
}
