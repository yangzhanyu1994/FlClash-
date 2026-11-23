import 'dart:async';
import 'dart:convert';

import 'package:fl_clash/common/common.dart';
import 'package:fl_clash/common/service_probe.dart';
import 'package:fl_clash/enum/enum.dart';
import 'package:fl_clash/models/models.dart';
import 'package:fl_clash/plugins/service.dart';
import 'package:fl_clash/providers/ip_quality.dart';
import 'package:fl_clash/providers/outbound_ip.dart';
import 'package:fl_clash/providers/providers.dart';
import 'package:fl_clash/providers/routed_probe.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

final widgetSyncProvider = Provider<WidgetSync>((ref) {
  final sync = WidgetSync(ref);
  ref.onDispose(sync.dispose);
  return sync;
});

class WidgetSync {
  WidgetSync(this._ref) {
    _ref.listen(isStartProvider, (_, _) => _push());
    _ref.listen(coreStatusProvider, (_, _) => _push());
    _ref.listen(trafficsProvider, (_, _) => _push());
    _ref.listen(requestCountProvider, (_, _) => _push());
    _ref.listen(outboundIpProbeProvider, (_, _) => _push());
    _ref.listen(patchClashConfigProvider, (_, _) => _push());
    _ref.listen(groupsProvider, (_, _) => _push());
    _ref.listen(delayDataSourceProvider, (_, _) => _push());
  }

  final Ref _ref;
  Timer? _timer;
  int _connections = 0;

  void start() {
    if (!system.isAndroid || _timer != null) {
      return;
    }
    _timer = Timer.periodic(const Duration(seconds: 3), (_) => _push());
    _push();
  }

  void dispose() {
    _timer?.cancel();
    _timer = null;
  }

  void _push() {
    unawaited(_sync());
  }

  Future<void> _sync() async {
    if (!system.isAndroid) {
      return;
    }
    try {
      final snapshot = await _collect();
      final preferences = await SharedPreferences.getInstance();
      await preferences.setString('widgetSnapshot', jsonEncode(snapshot));
      await service?.refreshWidget();
    } catch (error) {
      commonPrint.log('Widget sync failed: $error', logLevel: LogLevel.error);
    }
  }

  Future<Map<String, Object?>> _collect() async {
    final isStart = _ref.read(isStartProvider);
    final coreStatus = _ref.read(coreStatusProvider);
    final traffics = _ref.read(trafficsProvider);
    final latest = traffics.list.safeLast(const Traffic());
    final probe = _ref.read(outboundIpProbeProvider);
    final ipInfo = probe.entryOf(routedOutbound).value;
    final quality = ipInfo == null
        ? null
        : _ref.read(ipQualityProvider(ipInfo.ip)).value;
    final nodeName = _nodeName(probe, ipInfo);
    final running = isStart;
    final coreRunning = coreStatus == CoreStatus.connected;
    _connections = await _connectionCount();
    return {
      'running': running,
      'vpnConnected': running && coreRunning,
      'downloadSpeed': latest.down.toDouble(),
      'uploadSpeed': latest.up.toDouble(),
      'downloadLabel': _speedLabel(latest.down),
      'uploadLabel': _speedLabel(latest.up),
      'nodeName': nodeName,
      'country': ipInfo?.countryCode ?? '',
      'countryCode': ipInfo?.countryCode ?? '',
      'lineType': quality == null ? '' : _lineType(quality.type),
      'ipQualityLabel': quality == null ? '' : _qualityLabel(quality.level),
      'latencyMs': _latency(nodeName),
      'connections': _connections,
      'requests': _ref.read(requestCountProvider),
      'coreRunning': coreRunning,
      'mode': _modeLabel(_ref.read(patchClashConfigProvider).mode),
      'updatedAt': DateTime.now().millisecondsSinceEpoch,
      'abnormal': running && coreStatus == CoreStatus.disconnected,
    };
  }

  String _nodeName(RoutedProbeState<String, IpInfo> probe, IpInfo? ipInfo) {
    if (ipInfo != null) {
      for (final key in probe.entries.keys) {
        if (key.isNotEmpty &&
            key != routedOutbound &&
            probe.entries[key]?.value == ipInfo) {
          return key;
        }
      }
    }
    for (final group in _ref.read(groupsProvider)) {
      final now = group.now;
      if (now != null && now.isNotEmpty) {
        return now;
      }
    }
    return '';
  }

  int? _latency(String nodeName) {
    if (nodeName.isEmpty) {
      return null;
    }
    for (final byName in _ref.read(delayDataSourceProvider).values) {
      final value = byName[nodeName];
      if (value != null && value >= 0) {
        return value;
      }
    }
    return null;
  }

  Future<int> _connectionCount() async {
    try {
      return await _ref.read(coreHandlerProvider).getConnectionCount();
    } catch (_) {
      return _connections;
    }
  }

  String _speedLabel(num bytes) {
    final show = bytes.traffic;
    return '${show.value} ${show.unit}/s';
  }

  String _lineType(IpType type) {
    final localizations = currentAppLocalizations;
    return switch (type) {
      IpType.residential => localizations.ipTypeResidential,
      IpType.mobile => localizations.ipTypeMobile,
      IpType.business => localizations.ipTypeBusiness,
      IpType.hosting => localizations.ipTypeHosting,
    };
  }

  String _qualityLabel(IpQualityLevel level) {
    final localizations = currentAppLocalizations;
    return switch (level) {
      IpQualityLevel.good => localizations.ipQualityGood,
      IpQualityLevel.normal => localizations.ipQualityNormal,
      IpQualityLevel.risky => localizations.ipQualityRisky,
    };
  }

  String _modeLabel(Mode mode) {
    final localizations = currentAppLocalizations;
    return switch (mode) {
      Mode.rule => localizations.actionRuleMode,
      Mode.global => localizations.actionGlobalMode,
      Mode.direct => localizations.actionDirectMode,
    };
  }
}
