import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
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
      home: const WebViewPage(),
    );
  }
}

class WebViewPage extends StatefulWidget {
  const WebViewPage({super.key});

  @override
  State<WebViewPage> createState() => _WebViewPageState();
}

class _WebViewPageState extends State<WebViewPage> with WidgetsBindingObserver {
  late final WebViewController _controller;
  static const _platform = MethodChannel('com.hbx.short/overlay');
  bool _isLoading = true;

  static const String _webUrl =
      'https://xenox-short-production.up.railway.app';

  // JavaScript injected after every page load.
  // Defines window.XenoxAndroid which the existing web app already calls.
  static const String _bridgeJs = '''
(function() {
  if (window.__hbxBridgeReady) return;
  window.__hbxBridgeReady = true;

  var _overlayGranted = false;
  var _bubbleRunning  = false;

  window.XenoxAndroid = {
    hasOverlayPermission: function() {
      return _overlayGranted;
    },
    requestOverlayPermission: function() {
      AndroidBridge.postMessage(JSON.stringify({ action: 'requestOverlayPermission' }));
    },
    startBubble: function() {
      if (!_overlayGranted) { return false; }
      AndroidBridge.postMessage(JSON.stringify({ action: 'startBubble' }));
      _bubbleRunning = true;
      return true;
    },
    stopBubble: function() {
      AndroidBridge.postMessage(JSON.stringify({ action: 'stopBubble' }));
      _bubbleRunning = false;
    },
    isBubbleRunning: function() {
      return _bubbleRunning;
    },
    showHeadsUpNotification: function(title, msg) {
      AndroidBridge.postMessage(JSON.stringify({
        action: 'showNotification', title: title, message: msg
      }));
    },
    // Called by Flutter to sync state back into JS
    _setOverlayGranted: function(v) { _overlayGranted = v; },
    _setBubbleRunning:  function(v) { _bubbleRunning  = v; }
  };
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

  // Re-check overlay permission whenever the app comes back to foreground
  // (user may have just granted it in Settings).
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
      ..setNavigationDelegate(NavigationDelegate(
        onPageStarted: (_) => setState(() => _isLoading = true),
        onPageFinished: (_) async {
          await _controller.runJavaScript(_bridgeJs);
          await _syncOverlayPermission();
          if (mounted) setState(() => _isLoading = false);
        },
        onWebResourceError: (_) {
          if (mounted) setState(() => _isLoading = false);
        },
      ))
      ..addJavaScriptChannel(
        'AndroidBridge',
        onMessageReceived: (msg) => _handleBridgeMessage(msg.message),
      )
      ..loadRequest(Uri.parse(_webUrl));
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
          // Re-sync after a short delay to catch immediate grants
          await Future.delayed(const Duration(milliseconds: 800));
          await _syncOverlayPermission();
          break;

        case 'startBubble':
          await _platform.invokeMethod('startBubble');
          break;

        case 'stopBubble':
          await _platform.invokeMethod('stopBubble');
          break;

        case 'showNotification':
          await _platform.invokeMethod('showHeadsUpNotification', {
            'title': data['title'] ?? '',
            'message': data['message'] ?? '',
          });
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
        body: SafeArea(
          child: Stack(
            children: [
              WebViewWidget(controller: _controller),
              if (_isLoading)
                const Center(
                  child: CircularProgressIndicator(
                    color: Color(0xFF3B82F6),
                    strokeWidth: 2.5,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
