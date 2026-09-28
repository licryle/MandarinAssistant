import Foundation
import AppIntents
import crossPlatform
import WidgetKit

struct WordListEntity: AppEntity, Equatable {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Word List"
    static var defaultQuery = WordListQuery()

    var id: Int
    var name: String

    var displayRepresentation: DisplayRepresentation {
        let localized = (crossPlatform.CachedResources.shared.systemListNames as? [String: String])?[name] ?? name
        return DisplayRepresentation(title: "\(localized)")
    }
    
    static func == (lhs: WordListEntity, rhs: WordListEntity) -> Bool {
        return lhs.id == rhs.id
    }
}

extension WordListEntity: Identifiable {}

struct WordListQuery: EntityQuery {
    func entities(for identifiers: [Int]) async throws -> [WordListEntity] {
        let all = try await fetchAllLists()
        return all.filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [WordListEntity] {
        return try await fetchAllLists()
    }

    private func fetchAllLists() async throws -> [WordListEntity] {
        let services = crossPlatform.HSKAppServices.shared
        services.doInit(upToLevel: crossPlatform.HSKAppServicesPriority.Widget.shared)

        // Wait for initialization
        let isReady = try? await services.awaitReady(timeoutMs: 5000)

        if isReady == true {
            let lists = try await services.database.wordListDAO().getAllLists()
            return lists.map { wordListWithCount in
                WordListEntity(id: Int(wordListWithCount.id), name: wordListWithCount.name)
            }
        }
        
        return []
    }
}

struct HskWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Configure Widget"
    static var description = IntentDescription("Select word lists to display in the widget.")

    @Parameter(title: "Selected Lists")
    var selectedLists: [WordListEntity]?
}

// Interactivity Support (iOS 17+)
struct NextWordIntent: AppIntents.AppIntent {
    static var title: LocalizedStringResource = "Next Word"
    
    init() {}

    func perform() async throws -> some AppIntents.IntentResult {
        // Note: iOS 17+ automatically reloads the widget that triggered the intent.
        return .result()
    }
}

struct SpeakWordIntent: AppIntents.AppIntent {
    static var title: LocalizedStringResource = "Speak Word"
    static var openAppWhenRun: Bool = true
    
    @Parameter(title: "Word")
    var word: String

    init() { self.word = "" }
    init(word: String) { self.word = word }

    func perform() async throws -> some AppIntents.IntentResult {
        let searchQuery = crossPlatform.SearchQuery(query: word, ignoreAnnotation: true, inListName: nil)
        let encodedQuery = (searchQuery.description()).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let url = URL(string: "hskwidget://searchTTS?q=\(encodedQuery)") {
            await URLOpener.open(url: url)
        }

        return .result()
    }
}

struct OpenOCRIntent: AppIntents.AppIntent {
    static var title: LocalizedStringResource = "Open OCR"
    static var openAppWhenRun: Bool = true
    
    @Parameter(title: "Image Path")
    var imagePath: String

    init() { self.imagePath = "" }
    init(imagePath: String) { self.imagePath = imagePath }

    func perform() async throws -> some AppIntents.IntentResult {
        let encodedPath = imagePath.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let url = URL(string: "hskwidget://ocr?path=\(encodedPath)") {
            await URLOpener.open(url: url)
        }

        return .result()
    }
}

// Helper to bypass UIApplication.shared restriction in app extensions
struct URLOpener {
    @MainActor
    static func open(url: URL) {
        let selector = NSSelectorFromString("openURL:")
        // Use KVC to safely get the application instance without a compiler error
        if let applicationClass = NSClassFromString("UIApplication") as? NSObject.Type,
           let sharedApplication = applicationClass.value(forKeyPath: "sharedApplication") as? NSObject {
            sharedApplication.perform(selector, with: url)
        }
    }
}
