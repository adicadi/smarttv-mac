import WebKit

/// Owns one persistent WKWebView per streaming service so each keeps its own
/// session/back-stack, and surfaces playback/navigation failures visibly
/// instead of silently.
@MainActor
final class WebViewController: NSObject {
    /// Called with a human-readable message when a page fails to load or a
    /// <video> element reports a fatal error (typical DRM-failure signature).
    var onPlaybackError: ((String) -> Void)?
    /// Called with `true` when the current service starts navigating, and
    /// `false` once it finishes (or fails) loading.
    var onLoadingChange: ((Bool) -> Void)?

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
        // Bundled pages (cinema.html) fetch TMDB and embed vidking from a
        // file:// origin; allow that.
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        config.setValue(true, forKey: "allowUniversalAccessFromFileURLs")

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
        guard webView.url == nil else { return }
        if service.url.scheme == "smarttv" {
            // Bundled page, e.g. smarttv://cinema -> Resources/cinema.html
            let name = service.url.host ?? "cinema"
            if let fileURL = Bundle.main.url(forResource: name, withExtension: "html") {
                webView.loadFileURL(fileURL, allowingReadAccessTo: fileURL.deletingLastPathComponent())
            } else {
                onPlaybackError?("Bundled page '\(name).html' is missing from the app.")
            }
        } else {
            webView.load(URLRequest(url: service.url))
        }
    }

    // MARK: - Actions reachable from CommandRouter

    var canGoBack: Bool { current?.canGoBack ?? false }

    func goBack() { current?.goBack() }
    func goForward() { current?.goForward() }

    /// Pauses any <video>/<audio> in the currently displayed service. Called
    /// whenever playback should stop being audible/visible even though the
    /// WKWebView itself stays alive in the background (returning to the
    /// grid, the window being covered by another app's fullscreen space,
    /// HDMI disconnect, etc).
    func pauseCurrent() {
        current?.evaluateJavaScript(Self.pauseMediaJS, completionHandler: nil)
    }

    private static let pauseMediaJS = """
    document.querySelectorAll('video, audio').forEach(function(m) {
      try { m.pause(); } catch (e) {}
    });
    """

    /// Synthesizes an Enter keypress inside the page for remote "select"
    /// while a service is on screen.
    func sendEnterKey() {
        sendKey(key: "Enter", code: "Enter", keyCode: 13)
    }

    /// Remote "select"/OK while a service is on screen: toggles play/pause
    /// on the active <video> (like a TV remote's center button), falling
    /// back to a synthetic Enter keypress when there's no video to toggle
    /// (e.g. still browsing a page's own UI, such as My Cinema's grid).
    /// Only reaches same-origin video elements — a video inside a
    /// cross-origin embed (e.g. My Cinema's Vidking iframe) isn't reachable
    /// from here, so it falls back to Enter there too.
    func togglePlayPauseOrSelect() {
        current?.evaluateJavaScript(Self.togglePlayPauseJS) { [weak self] result, _ in
            MainActor.assumeIsolated {
                guard let self, (result as? String) != "toggled" else { return }
                self.sendEnterKey()
            }
        }
    }

    private static let togglePlayPauseJS = """
    (function() {
      const videos = Array.from(document.querySelectorAll('video'));
      if (videos.length === 0) return 'no-video';
      const target = videos.reduce(function(a, b) {
        return (b.clientWidth * b.clientHeight) > (a.clientWidth * a.clientHeight) ? b : a;
      });
      if (target.paused) { target.play(); } else { target.pause(); }
      return 'toggled';
    })();
    """

    /// Skips the active video back/forward 10s. Silently does nothing when
    /// there's no same-origin <video> to seek (e.g. My Cinema's Vidking
    /// iframe, or still browsing a page's own UI).
    func seek(direction: String) {
        let delta = direction == "back" ? -10 : 10
        let js = """
        (function() {
          const videos = Array.from(document.querySelectorAll('video'));
          if (videos.length === 0) return;
          const target = videos.reduce(function(a, b) {
            return (b.clientWidth * b.clientHeight) > (a.clientWidth * a.clientHeight) ? b : a;
          });
          target.currentTime = Math.max(0, target.currentTime + (\(delta)));
        })();
        """
        current?.evaluateJavaScript(js, completionHandler: nil)
    }

    /// Toggles fullscreen on the active <video> (or exits fullscreen if the
    /// page is already showing one). Same same-origin caveat as
    /// togglePlayPauseOrSelect: a cross-origin embed's video can't be reached.
    func toggleFullscreen() {
        current?.evaluateJavaScript(Self.toggleFullscreenJS, completionHandler: nil)
    }

    private static let toggleFullscreenJS = """
    (function() {
      if (document.fullscreenElement) { document.exitFullscreen(); return; }
      const videos = Array.from(document.querySelectorAll('video'));
      if (videos.length === 0) return;
      const target = videos.reduce(function(a, b) {
        return (b.clientWidth * b.clientHeight) > (a.clientWidth * a.clientHeight) ? b : a;
      });
      target.requestFullscreen();
    })();
    """

    /// Forwards remote d-pad navigation into the page as arrow-key events
    /// (drives cinema.html's focus model; many streaming UIs honor them too).
    func sendArrowKey(_ direction: MoveDirection) {
        let name: String
        let keyCode: Int
        switch direction {
        case .up: name = "ArrowUp"; keyCode = 38
        case .down: name = "ArrowDown"; keyCode = 40
        case .left: name = "ArrowLeft"; keyCode = 37
        case .right: name = "ArrowRight"; keyCode = 39
        }
        sendKey(key: name, code: name, keyCode: keyCode)
    }

    private func sendKey(key: String, code: String, keyCode: Int) {
        let js = """
        (function() {
          const t = document.activeElement || document.body;
          for (const type of ['keydown', 'keyup']) {
            t.dispatchEvent(new KeyboardEvent(type, {key: '\(key)', code: '\(code)', keyCode: \(keyCode), which: \(keyCode), bubbles: true}));
          }
        })();
        """
        current?.evaluateJavaScript(js, completionHandler: nil)
    }

    /// Best-effort: focuses YouTube's search box (clicking the search icon
    /// first if the "Living Room" TV UI has it hidden) and submits `text` as
    /// a search. YouTube's DOM/class names aren't a stable public API, so
    /// this tries a few known selectors — including the "ytlr-" prefix used
    /// by youtube.com/tv's web components — and silently no-ops if none of
    /// them match (e.g. after a YouTube redesign).
    func youTubeVoiceSearch(_ text: String) {
        guard let payload = try? JSONEncoder().encode(text),
              let jsString = String(data: payload, encoding: .utf8) else { return }
        let js = """
        (function() {
          const text = \(jsString);
          const selector = 'input[name="search_query"], input[type="search"], ytlr-text-box input, [role="searchbox"] input';
          function submit(input) {
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(input, text);
            input.dispatchEvent(new Event('input', {bubbles: true}));
            input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
          }
          const existing = document.querySelector(selector);
          if (existing) { existing.focus(); submit(existing); return; }
          const trigger = document.querySelector('[aria-label="Search" i], button[aria-label*="search" i], ytlr-search-container, tp-yt-paper-icon-button#search-icon, #search-icon-legacy');
          if (trigger) { trigger.click(); }
          setTimeout(function() {
            const input = document.querySelector(selector);
            if (input) { input.focus(); submit(input); }
          }, 400);
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
    nonisolated func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        Task { @MainActor in self.onLoadingChange?(true) }
    }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Task { @MainActor in self.onLoadingChange?(false) }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in self.onLoadingChange?(false) }
        reportNavigationError(error)
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in self.onLoadingChange?(false) }
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
