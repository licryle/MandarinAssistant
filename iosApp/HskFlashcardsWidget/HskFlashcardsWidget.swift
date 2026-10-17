import WidgetKit
import SwiftUI
import crossPlatform

struct HskEntry: TimelineEntry {
    let date: Date
    let word: String
    let pinyin: String
    let definition: String
    let level: String
    let isEmpty: Bool
}

struct Provider: AppIntentTimelineProvider {
    private var resources: CachedResources {
        crossPlatform.CachedResources.shared
    }

    private func fetchEntry(for configuration: HskWidgetConfigurationIntent) async -> HskEntry {
        let services = crossPlatform.HSKAppServices.shared
        services.doInit(upToLevel: crossPlatform.HSKAppServicesPriority.Widget.shared)

        // Wait for initialization
        let isReady = try? await services.awaitReady(timeoutMs: 5000)
        
        if isReady == true {
            let selectedListIds = configuration.selectedLists?.map { KotlinLong(value: Int64($0.id)) } ?? []

            if !selectedListIds.isEmpty {
                // Use the DAO directly to pick a random word from the selected lists
                let word = try? await services.database.annotatedChineseWordDAO().getRandomWordFromLists(
                    listIds: selectedListIds, 
                    bannedWords: KotlinArray(size: 0, init: { _ in "" as NSString })
                )

                if let word = word {
                    let pinyinList = word.pinyins as! [crossPlatform.Pinyin]
                    let pinyinStr = pinyinList.map { $0.syllable }.joined(separator: " ")
                    let definitionMap = word.word?.definition ?? [:]
                    let dictionaryLocale = crossPlatform.Locale.companion.resolve(preferred: services.appPreferences.dictionaryLocale.value)
                    let definition = (definitionMap[dictionaryLocale] as? String) ?? (word.annotation?.notes as? String) ?? ""
                    // HSK_Level.NOT_HSK is not a level: leave the label empty, so that the
                    // widget simply doesn't show it, as the Android widget does.
                    let hskLevel = word.hskLevel
                    let levelStr = hskLevel == crossPlatform.HSK_Level.notHsk ? "" : hskLevel.name

                    return HskEntry(
                        date: Date(),
                        word: word.simplified,
                        pinyin: pinyinStr,
                        definition: definition,
                        level: levelStr,
                        isEmpty: false
                    )
                }
            }
        }

        return HskEntry(
            date: Date(),
            word: "",
            pinyin: "",
            definition: "",
            level: "",
            isEmpty: true
        )
    }

    func placeholder(in context: Context) -> HskEntry {
        HskEntry(
            date: Date(),
            word: resources.placeholderWord,
            pinyin: resources.placeholderPinyin,
            definition: resources.placeholderDefinition,
            level: resources.placeholderLevel,
            isEmpty: false
        )
    }

    func snapshot(for configuration: HskWidgetConfigurationIntent, in context: Context) async -> HskEntry {
        if context.isPreview {
            // Return mock data for the widget gallery
            return placeholder(in: context)
        }
        
        return await fetchEntry(for: configuration)
    }

    func timeline(for configuration: HskWidgetConfigurationIntent, in context: Context) async -> Timeline<HskEntry> {
        let entry = await fetchEntry(for: configuration)
        return Timeline(entries: [entry], policy: .atEnd)
    }
}

struct HskFlashcardsWidget: Widget {
    let kind: String = "MandarinAssistantWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: HskWidgetConfigurationIntent.self, provider: Provider()) { entry in
            HskFlashcardsWidgetView(entry: entry)
        }
        .configurationDisplayName(crossPlatform.CachedResources.shared.widgetListName)
        .description(crossPlatform.CachedResources.shared.widgetListDescription)
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}
