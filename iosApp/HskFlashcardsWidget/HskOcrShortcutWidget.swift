import WidgetKit
import SwiftUI
import crossPlatform

struct SimpleProvider: TimelineProvider {
    func placeholder(in context: Context) -> HskEntry {
        HskEntry(date: Date(), word: "", pinyin: "", definition: "", level: "", isEmpty: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (HskEntry) -> ()) {
        let entry = HskEntry(date: Date(), word: "", pinyin: "", definition: "", level: "", isEmpty: true)
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> ()) {
        let entry = HskEntry(date: Date(), word: "", pinyin: "", definition: "", level: "", isEmpty: true)
        let timeline = Timeline(entries: [entry], policy: .atEnd)
        completion(timeline)
    }
}

struct HskOcrShortcutWidget: Widget {
    let kind: String = "MandarinAssistantOCRShortcut"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SimpleProvider()) { entry in
            HskFlashcardsWidgetView(entry: entry)
        }
        .configurationDisplayName(LocalizedStringResource("Take Picture/OCR Shortcut"))
        .description(LocalizedStringResource("Quickly take a picture to extract Mandarin text."))
        .supportedFamilies([.accessoryCircular])
    }
}
