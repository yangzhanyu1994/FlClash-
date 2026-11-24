import 'package:fl_clash/common/common.dart';
import 'package:flutter/services.dart';

class HomeWidgetChannel {
  HomeWidgetChannel()
    : _channel = const MethodChannel('$packageName/home_widget');

  final MethodChannel _channel;

  Future<void> publish(String json) {
    return _channel.invokeMethod<void>('publish', json);
  }

  Future<int> installed() async {
    final count = await _channel.invokeMethod<int>('installed');
    return count ?? 0;
  }
}

final homeWidgetChannel = system.isAndroid ? HomeWidgetChannel() : null;
