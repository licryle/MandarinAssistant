import UIKit
import UniformTypeIdentifiers

class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Find the first attachment that is either an image, a URL, or plain text
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = extensionItem.attachments else {
            self.extensionContext?.completeRequest(returningItems: nil)
            return
        }
        
        // 1. Try to find an image first (highest priority for OCR)
        for attachment in attachments {
            if let typeIdentifier = attachment.registeredTypeIdentifiers.first(where: { 
                UTType($0)?.conforms(to: .image) == true 
            }) {
                handleImage(attachment, typeIdentifier: typeIdentifier)
                return
            }
        }
        
        // 2. Try to find a URL
        for attachment in attachments {
            if attachment.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                handleURL(attachment)
                return
            }
        }
        
        // 3. Try to find plain text
        for attachment in attachments {
            if attachment.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                handleText(attachment)
                return
            }
        }
        
        // If nothing found, just close
        self.extensionContext?.completeRequest(returningItems: nil)
    }
    
    private func handleImage(_ attachment: NSItemProvider, typeIdentifier: String) {
        attachment.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { [weak self] (data, error) in
            if let url = data as? URL {
                if let imageData = try? Data(contentsOf: url) {
                    self?.saveImageAndOpenApp(data: imageData)
                    return
                }
            } else if let image = data as? UIImage {
                if let imageData = image.jpegData(compressionQuality: 0.8) {
                    self?.saveImageAndOpenApp(data: imageData)
                    return
                }
            } else if let imageData = data as? Data {
                self?.saveImageAndOpenApp(data: imageData)
                return
            }
            
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
    }
    
    private func handleText(_ attachment: NSItemProvider) {
        attachment.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] (data, error) in
            if let text = data as? String {
                let encodedText = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                self?.openMainApp(url: "hskwidget://search?q=\(encodedText)&src=share")
            } else {
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        }
    }
    
    private func handleURL(_ attachment: NSItemProvider) {
        attachment.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (data, error) in
            guard let url = data as? URL else {
                self?.extensionContext?.completeRequest(returningItems: nil)
                return
            }
            
            // Check if it's a file URL pointing to an image
            if url.isFileURL {
                if let imageData = try? Data(contentsOf: url), UIImage(data: imageData) != nil {
                    self?.saveImageAndOpenApp(data: imageData)
                    return
                }
            }

            // For web URLs, try to download and check if it's an image (e.g. shared from browser)
            let task = URLSession.shared.dataTask(with: url) { [weak self] (downloadedData, _, _) in
                if let downloadedData = downloadedData, UIImage(data: downloadedData) != nil {
                    self?.saveImageAndOpenApp(data: downloadedData)
                } else {
                    // Not an image, treat as a regular search for the URL
                    let encodedUrl = url.absoluteString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                    self?.openMainApp(url: "hskwidget://search?q=\(encodedUrl)&src=share")
                }
            }
            task.resume()
        }
    }
    
    private func saveImageAndOpenApp(data: Data) {
        let fileManager = FileManager.default
        if let groupURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: "group.net.vertex8.hskwidgetassist2") {
            let targetURL = groupURL.appendingPathComponent("shared_ocr_input.jpg")
            try? fileManager.removeItem(at: targetURL)
            try? data.write(to: targetURL)
            
            let encodedPath = targetURL.path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            openMainApp(url: "hskwidget://ocr?path=\(encodedPath)&src=share")
        } else {
            self.extensionContext?.completeRequest(returningItems: nil)
        }
    }

    private func openMainApp(url: String) {
        guard let nsUrl = URL(string: url) else { 
            self.extensionContext?.completeRequest(returningItems: nil)
            return 
        }
        
        DispatchQueue.main.async {
            var responder: UIResponder? = self
            let selector = NSSelectorFromString("openURL:")
            
            while responder != nil {
                if responder?.responds(to: selector) == true {
                    responder?.perform(selector, with: nsUrl)
                    break
                }
                responder = responder?.next
            }
            
            if responder == nil {
                if let applicationClass = NSClassFromString("UIApplication") as? NSObject.Type,
                   let sharedApplication = applicationClass.value(forKeyPath: "sharedApplication") as? NSObject {
                    sharedApplication.perform(selector, with: nsUrl)
                }
            }

            // Small delay to ensure the URL trigger is registered before the extension context is killed
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self.extensionContext?.completeRequest(returningItems: nil)
            }
        }
    }
}
