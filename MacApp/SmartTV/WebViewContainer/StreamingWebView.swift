import SwiftUI
import WebKit

/// Hosts an existing WKWebView (owned by WebViewController) in SwiftUI.
struct StreamingWebView: NSViewRepresentable {
    let webView: WKWebView

    func makeNSView(context: Context) -> WKWebView {
        webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        // The web view instance is swapped by ContentView when the service
        // changes; nothing to update in place.
    }
}
