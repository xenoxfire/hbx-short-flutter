import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Edge-to-edge full screen — no status bar, no navigation bar
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    systemNavigationBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));

  // Lock to portrait
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  runApp(const HBXShortApp());
}

class HBXShortApp extends StatelessWidget {
  const HBXShortApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HBX Short',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: Colors.black,
      ),
      home: const SplashScreen(),
    );
  }
}

// ─── Splash Screen ────────────────────────────────────────────────────────────

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _fadeAnim;
  late Animation<double> _scaleAnim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    );
    _fadeAnim = CurvedAnimation(parent: _ctrl, curve: Curves.easeIn);
    _scaleAnim = Tween<double>(begin: 0.82, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutBack));
    _ctrl.forward();

    Future.delayed(const Duration(milliseconds: 1600), () {
      if (mounted) {
        Navigator.of(context).pushReplacement(
          PageRouteBuilder(
            pageBuilder: (_, __, ___) => const WebViewPage(),
            transitionsBuilder: (_, anim, __, child) =>
                FadeTransition(opacity: anim, child: child),
            transitionDuration: const Duration(milliseconds: 400),
          ),
        );
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF080E1A),
      body: Center(
        child: FadeTransition(
          opacity: _fadeAnim,
          child: ScaleTransition(
            scale: _scaleAnim,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 110,
                  height: 110,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: [
                      BoxShadow(
                        color: const Color(0xFF3B82F6).withOpacity(0.45),
                        blurRadius: 32,
                        spreadRadius: 6,
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(24),
                    child: Image.asset(
                      'assets/icon/app_icon.png',
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        decoration: BoxDecoration(
                          color: const Color(0xFF0D1117),
                          borderRadius: BorderRadius.circular(24),
                          border: Border.all(
                              color: const Color(0xFF3B82F6), width: 2),
                        ),
                        child: const Center(
                          child: Text(
                            'HBX',
                            style: TextStyle(
                              color: Color(0xFF3B82F6),
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 22),
                const Text(
                  'HBX Short',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 26,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Loading...',
                  style: TextStyle(
                    color: Color(0xFF6B7280),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ─── WebView Page ─────────────────────────────────────────────────────────────

class WebViewPage extends StatefulWidget {
  const WebViewPage({super.key});

  @override
  State<WebViewPage> createState() => _WebViewPageState();
}

class _WebViewPageState extends State<WebViewPage>
    with WidgetsBindingObserver {
  late final WebViewController _controller;
  static const _platform = MethodChannel('com.hbx.short/overlay');
  bool _isLoading = true;
  bool _hasError = false;

  static const String _webUrl =
      'https://xenox-short-production.up.railway.app';

  // JS Bridge injected after every page load
  static const String _bridgeJs = r'''
(function() {
  if (window.__hbxBridgeReady) return;
  window.__hbxBridgeReady = true;

  var _overlayGranted = false;
  var _bubbleRunning  = false;
  var _bubbleSizeDp   = 62;

  window.XenoxAndroid = {
    hasOverlayPermission: function() { return _overlayGranted; },

    requestOverlayPermission: function() {
      AndroidBridge.postMessage(JSON.stringify({ action: 'requestOverlayPermission' }));
    },

    startBubble: function() {
      if (!_overlayGranted) { return false; }
      AndroidBridge.postMessage(JSON.stringify({ action: 'startBubble', sizeDp: _bubbleSizeDp }));
      _bubbleRunning = true;
      return true;
    },

    stopBubble: function() {
      AndroidBridge.postMessage(JSON.stringify({ action: 'stopBubble' }));
      _bubbleRunning = false;
    },

    isBubbleRunning: function() { return _bubbleRunning; },

    setBubbleSize: function(dp) {
      _bubbleSizeDp = dp;
      AndroidBridge.postMessage(JSON.stringify({ action: 'setBubbleSize', sizeDp: dp }));
    },

    showHeadsUpNotification: function(title, msg) {
      AndroidBridge.postMessage(JSON.stringify({
        action: 'showNotification', title: title, message: msg
      }));
    },

    shareText: function(text) {
      AndroidBridge.postMessage(JSON.stringify({ action: 'shareText', text: text }));
    },

    copyToClipboard: function(text) {
      AndroidBridge.postMessage(JSON.stringify({ action: 'copyClipboard', text: text }));
    },

    getFcmToken: function() {
      AndroidBridge.postMessage(JSON.stringify({ action: 'getFcmToken' }));
    },

    // Sync Float Sheet config to Android so native bubble can use it
    syncBubbleConfig: function(configJson) {
      AndroidBridge.postMessage(JSON.stringify({
        action: 'syncBubbleConfig',
        configJson: typeof configJson === 'string' ? configJson : JSON.stringify(configJson)
      }));
    },

    // Called by Flutter to sync state back into JS
    _setOverlayGranted: function(v) { _overlayGranted = v; },
    _setBubbleRunning:  function(v) { _bubbleRunning  = v; },
    _onFcmToken:        function(t) {
      if (window.onFcmToken) window.onFcmToken(t);
    },
  };

  // Smooth scrolling
  document.documentElement.style.scrollBehavior = 'smooth';
  document.documentElement.style.webkitOverflowScrolling = 'touch';

  // Disable text selection flash on tap (native feel)
  var noSelectStyle = document.createElement('style');
  noSelectStyle.textContent =
    '* { -webkit-tap-highlight-color: transparent; }' +
    'input, textarea { -webkit-user-select: text !important; user-select: text !important; }';
  document.head.appendChild(noSelectStyle);
})();
''';

  // Performance-boosting JS injected once
  static const String _perfJs = r'''
(function() {
  // Lazy-load images that are off-screen
  if ('loading' in HTMLImageElement.prototype) {
    document.querySelectorAll('img').forEach(function(img) {
      if (!img.getAttribute('loading')) img.loading = 'lazy';
    });
  }
  // Preconnect to origins used by the app
  ['https://fonts.googleapis.com','https://fonts.gstatic.com'].forEach(function(origin) {
    var link = document.createElement('link');
    link.rel  = 'preconnect';
    link.href = origin;
    link.crossOrigin = '';
    document.head.appendChild(link);
  });
})();
''';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initController();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _syncOverlayPermission();
    }
  }

  void _initController() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.black)
      ..enableZoom(false)
      ..setUserAgent(
        'Mozilla/5.0 (Linux; Android 13; Pixel 7) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/114.0.0.0 Mobile Safari/537.36 HBXShortApp/1.0',
      )
      ..setNavigationDelegate(NavigationDelegate(
        onPageStarted: (_) {
          if (mounted) setState(() { _isLoading = true; _hasError = false; });
        },
        onPageFinished: (_) async {
          await _controller.runJavaScript(_bridgeJs);
          await _controller.runJavaScript(_perfJs);
          await _syncOverlayPermission();
          if (mounted) setState(() => _isLoading = false);
        },
        onWebResourceError: (err) {
          if (err.isForMainFrame == true && mounted) {
            setState(() { _isLoading = false; _hasError = true; });
          }
        },
        onNavigationRequest: (req) => NavigationDecision.navigate,
      ))
      ..addJavaScriptChannel(
        'AndroidBridge',
        onMessageReceived: (msg) => _handleBridgeMessage(msg.message),
      )
      ..loadRequest(
        Uri.parse(_webUrl),
        headers: const {
          'Cache-Control': 'max-age=3600',
          'Accept-Encoding': 'gzip, deflate, br',
        },
      );
  }

  Future<void> _syncOverlayPermission() async {
    try {
      final granted =
          await _platform.invokeMethod<bool>('hasOverlayPermission') ?? false;
      await _controller.runJavaScript(
        'window.XenoxAndroid && window.XenoxAndroid._setOverlayGranted($granted);',
      );
    } catch (_) {}
  }

  Future<void> _handleBridgeMessage(String raw) async {
    try {
      final data = jsonDecode(raw) as Map<String, dynamic>;
      final action = data['action'] as String?;

      switch (action) {
        case 'requestOverlayPermission':
          await _platform.invokeMethod('requestOverlayPermission');
          await Future.delayed(const Duration(milliseconds: 800));
          await _syncOverlayPermission();
          break;

        case 'startBubble':
          final sizeDp = (data['sizeDp'] as num?)?.toInt() ?? 62;
          await _platform.invokeMethod('startBubble', {'sizeDp': sizeDp});
          break;

        case 'stopBubble':
          await _platform.invokeMethod('stopBubble');
          break;

        case 'setBubbleSize':
          final sizeDp = (data['sizeDp'] as num?)?.toInt() ?? 62;
          await _platform.invokeMethod('setBubbleSize', {'sizeDp': sizeDp});
          break;

        case 'showNotification':
          await _platform.invokeMethod('showHeadsUpNotification', {
            'title': data['title'] ?? '',
            'message': data['message'] ?? '',
          });
          break;

        case 'shareText':
          await _platform.invokeMethod('shareText', {'text': data['text'] ?? ''});
          break;

        case 'copyClipboard':
          await _platform.invokeMethod('copyToClipboard', {'text': data['text'] ?? ''});
          break;

        case 'syncBubbleConfig':
          await _platform.invokeMethod('syncBubbleConfig', {
            'configJson': data['configJson'] ?? '{}',
          });
          break;

        case 'getFcmToken':
          try {
            final token = await _platform.invokeMethod<String>('getFcmToken');
            if (token != null) {
              await _controller.runJavaScript(
                'window.XenoxAndroid && window.XenoxAndroid._onFcmToken(${jsonEncode(token)});',
              );
            }
          } catch (_) {}
          break;
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvoked: (didPop) async {
        if (!didPop && await _controller.canGoBack()) {
          await _controller.goBack();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        // No SafeArea — true full screen
        body: Stack(
          children: [
            WebViewWidget(controller: _controller),

            // Loading indicator
            if (_isLoading)
              Container(
                color: const Color(0xFF080E1A),
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const SizedBox(
                        width: 48,
                        height: 48,
                        child: CircularProgressIndicator(
                          color: Color(0xFF3B82F6),
                          strokeWidth: 2.5,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Loading HBX Short...',
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.5),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ),

            // Error screen
            if (_hasError && !_isLoading)
              Container(
                color: const Color(0xFF080E1A),
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.wifi_off, color: Color(0xFF6B7280), size: 56),
                      const SizedBox(height: 16),
                      const Text(
                        'No connection',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Check your internet and try again',
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.5),
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 28),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF3B82F6),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12)),
                          padding: const EdgeInsets.symmetric(
                              horizontal: 32, vertical: 14),
                        ),
                        onPressed: () {
                          setState(() { _hasError = false; _isLoading = true; });
                          _controller.reload();
                        },
                        child: const Text('Retry',
                            style: TextStyle(color: Colors.white, fontSize: 16)),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
