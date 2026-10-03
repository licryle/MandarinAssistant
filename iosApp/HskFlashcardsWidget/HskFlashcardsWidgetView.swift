import SwiftUI
import WidgetKit
import AppIntents
import crossPlatform

struct HskFlashcardsWidgetView: View {
    var entry: HskEntry

    @Environment(\.widgetFamily) var family

    var body: some View {
        Group {
            if family == .accessoryRectangular {
                LockScreenRectangularView(entry: entry)
            } else if family == .accessoryCircular {
                LockScreenCircularOCRView()
            } else if !entry.isEmpty {
                VStack(spacing: 4) {
                    // Top Row: Reload - Level - Speak
                    HStack {
                        Button(intent: NextWordIntent()) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 12))
                                .frame(width: 24, height: 24)
                                .background(Color.secondary.opacity(0.1))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)

                        Spacer()

                        if !entry.level.isEmpty {
                            Text(entry.level)
                                .font(.system(size: 10, weight: .medium))
                                .padding(.horizontal, 4)
                                .padding(.vertical, 2)
                                .background(Color.secondary.opacity(0.1))
                                .cornerRadius(4)
                        }

                        Spacer()

                        Link(destination:  URL(string: "hskwidget://searchTTS?q=\((crossPlatform.SearchQuery(query: entry.word, ignoreAnnotation: true, inListName: nil).description()).addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? "")&src=widget")!) {
                            Image(systemName: "speaker.wave.2")
                                .font(.system(size: 12))
                                .frame(width: 24, height: 24)
                                .background(Color.secondary.opacity(0.1))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }

                    // Middle Content: Pinyin - Simplified - Definition
                    Link(destination: URL(string: "hskwidget://search?q=\((crossPlatform.SearchQuery(query: entry.word, ignoreAnnotation: true, inListName: nil).description()).addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? "")&src=widget")!) {
                        VStack(spacing: 2) {
                            Spacer(minLength: 0)

                            Text(entry.pinyin)
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                                .lineLimit(1)

                            Text(entry.word)
                                .font(.system(size: 30, weight: .bold))
                                .minimumScaleFactor(0.5)
                                .lineLimit(1)

                            Text(entry.definition)
                                .font(.system(size: 13))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .foregroundColor(.primary.opacity(0.8))

                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
            } else {
                Link(destination: URL(string: "hskwidget://configure")!) {
                    VStack(spacing: 8) {
                        Spacer()
                        Image("AppIconSmall")
                            .resizable()
                            .frame(width: 60, height: 60)
                            .cornerRadius(12)

                        Text(crossPlatform.CachedResources.shared.widgetNotConfigured)
                            .font(.system(size: 14, weight: .medium))
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                }
            }
        }
        .containerBackground(for: .widget) {
            if family == .accessoryRectangular || family == .accessoryCircular {
                ZStack {
                    AccessoryWidgetBackground()
                    Color.black.opacity(0.3)
                }
            } else {
                Color(UIColor.systemBackground)
            }
        }
    }
}

struct LockScreenRectangularView: View {
    var entry: HskEntry

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            
            if !entry.isEmpty {
                HStack(spacing: 4) {
                    Link(destination: URL(string: "hskwidget://search?q=\((crossPlatform.SearchQuery(query: entry.word, ignoreAnnotation: true, inListName: nil).description()).addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? "")&src=widget")!) {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(entry.word)
                                    .font(.headline)
                                    .widgetAccentable()
                                Text(entry.pinyin)
                                    .font(.caption)
                                    .lineLimit(1)
                                    .widgetAccentable()
                            }
                            Text(entry.definition)
                                .font(.caption2)
                                .lineLimit(2)
                                .widgetAccentable()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    VStack(spacing: 0) {
                        Link(destination:  URL(string: "hskwidget://searchTTS?q=\((crossPlatform.SearchQuery(query: entry.word, ignoreAnnotation: true, inListName: nil).description()).addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? "")&src=widget")!) {
                            Image(systemName: "speaker.wave.2")
                                .font(.system(size: 13))
                                .frame(width: 23, height: 23)
                                .background(Color.secondary.opacity(0.4))
                                .clipShape(Circle())
                                .widgetAccentable()
                        }
                        .buttonStyle(.plain)

                        Spacer(minLength: 0)

                        Button(intent: NextWordIntent()) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 13))
                                .frame(width: 23, height: 23)
                                .background(Color.secondary.opacity(0.4))
                                .clipShape(Circle())
                                .widgetAccentable()
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxHeight: .infinity)
                }
                .padding(.horizontal, 4)
                .padding(.vertical, 4)
            } else {
                // Unconfigured state
                VStack(alignment: .leading, spacing: 0) {
                    Text(crossPlatform.CachedResources.shared.appName)
                        .font(.caption)
                        .widgetAccentable()
                    Text(crossPlatform.CachedResources.shared.widgetNotConfigured)
                        .font(.caption2)
                        .lineLimit(2)
                        .widgetAccentable()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
                .padding(.vertical, 4)
            }
        }
        .cornerRadius(12)
    }
}

struct LockScreenCircularOCRView: View {
    var body: some View {
        ZStack {
            Image("AppIconSmall")
                .resizable()
                .scaledToFit()
                .clipShape(Circle())
                .opacity(0.8)
            
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Image(systemName: "camera.fill")
                        .font(.system(size: 14, weight: .bold))
                        .widgetAccentable()
                }
            }
            .padding(4)
        }
        .widgetURL(URL(string: "hskwidget://ocr?src=widget")!)
    }
}
