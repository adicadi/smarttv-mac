import WebKit

/// Owns one persistent WKWebView per streaming service so each keeps its own
/// session/back-stack, and surfaces playback/navigation failures visibly
/// instead of silently.
@MainActor
final class WebViewController: NSObject {
    /// Called with a human-readable message when a page fails to load or a
    /// <video> element reports a fatal error (typical DRM-failure signature).
    var onPlaybackError: ((String) -> Void)?

    private var webViews: [String: WKWebView] = [:]
    private var current: WKWebView?
    private var currentServiceName: String = ""

    // MARK: - Lifecycle

    func webView(for service: StreamingService) -> WKWebView {
        if let existing = webViews[service.id] {
            return existing
        }
        let config = WKWebViewConfiguration()
        // Persistent store: streaming logins must survive relaunches.
        config.websiteDataStore = .default()
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.isElementFullscreenEnabled = true

        // Report fatal <video> errors back to native code so DRM failures are
        // visible in the UI rather than a black rectangle.
        let script = WKUserScript(
            source: Self.videoErrorProbeJS,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        )
        config.userContentController.addUserScript(script)
        config.userContentController.add(self, name: "videoError")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        // Some services gate features on the browser UA; Safari's UA gives
        // the best FairPlay/EME path in WebKit.
        webView.customUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        webViews[service.id] = webView
        return webView
    }

    func load(_ service: StreamingService) {
        let webView = webView(for: service)
        current = webView
        currentServiceName = service.name
        // Only (re)load if the view is empty; returning to a service should
        // resume where the user left off, not restart it.
        if webView.url == nil {
            webView.load(URLRequest(url: service.url))
        }
    }

    // MARK: - Actions reachable from CommandRouter

    var canGoBack: Bool { current?.canGoBack ?? false }

    func goBack() { current?.goBack() }
    func goForward() { current?.goForward() }

    /// Synthesizes an Enter keypress inside the page for remote "select"
    /// while a service is on screen.
    func sendEnterKey() {
        let js = """
        (function() {
          const t = document.activeElement || document.body;
          for (const type of ['keydown', 'keyup']) {
            t.dispatchEvent(new KeyboardEvent(type, {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
          }
        })();
        """
        current?.evaluateJavaScript(js, completionHandler: nil)
    }

    // MARK: - Video error probe

    private static let videoErrorProbeJS = """
    (function() {
      function watch(video) {
        if (video.__smarttvWatched) return;
        video.__smarttvWatched = true;
        video.addEventListener('error', function() {
          const err = video.error;
          window.webkit.messageHandlers.videoError.postMessage({
            code: err ? err.code : -1,
            message: err && err.message ? err.message : 'unknown'
          });
        });
      }
      new MutationObserver(function() {
        document.querySelectorAll('video').forEach(watch);
      }).observe(document.documentElement, {childList: true, subtree: true});
      document.querySelectorAll('video').forEach(watch);
    })();
    """
}

extension WebViewController: WKNavigationDelegate {
    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        reportNavigationError(error)
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        reportNavigationError(error)
    }

    private nonisolated func reportNavigationError(_ error: Error) {
        let nsError = error as NSError
        // Ignore cancellations (common during in-page redirects).
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled { return }
        if nsError.domain == "WebKitErrorDomain" && nsError.code == 102 { return } // frame load interrupted
        let description = nsError.localizedDescription
        Task { @MainActor in
            self.onPlaybackError?("Failed to load \(self.currentServiceName): \(description)")
        }
    }
}

extension WebViewController: WKScriptMessageHandler {
    nonisolated func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        // WKScriptMessage is delivered on the main thread.
        let code = MainActor.assumeIsolated { () -> Int? in
            guard message.name == "videoError" else { return nil }
            return (message.body as? [String: Any])?["code"] as? Int ?? -1
        }
        guard let code else { return }
        Task { @MainActor in
            // MEDIA_ERR_SRC_NOT_SUPPORTED (4) / MEDIA_ERR_DECODE (3) are the
            // usual signatures of a DRM negotiation failure.
            let hint = (code == 3 || code == 4) ? " This service's DRM may not be supported in this WebKit version." : ""
            self.onPlaybackError?("\(self.currentServiceName) video failed to play (media error \(code)).\(hint)")
        }
    }
}
