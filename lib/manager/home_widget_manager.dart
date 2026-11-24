import 'dart:async';
import 'dart:convert';

import 'package:fl_clash/common/common.dart';
import 'package:fl_clash/common/service_probe.dart';
import 'package:fl_clash/database/database.dart';
import 'package:fl_clash/enum/enum.dart';
import 'package:fl_clash/features/home_widget/home_widget.dart';
import 'package:fl_clash/models/models.dart';
import 'package:fl_clash/plugins/home_widget.dart';
import 'package:fl_clash/providers/ip_quality.dart';
import 'package:fl_clash/providers/outbound_ip.dart';
import 'package:fl_clash/providers/providers.dart';
import 'package:fl_clash/providers/routed_probe.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class HomeWidgetCounters {
  const HomeWidgetCounters({this.rate, this.session, this.connections});

  final Traffic? rate;
  final Traffic? session;
  final int? connections;
}

class HomeWidgetManager extends ConsumerStatefulWidget {
  const HomeWidgetManager({
    super.key,
    required this.child,
    this.enabled = false,
    this.installed,
    this.publish,
    this.traffic,
    this.checkedAt,
  });

  final Widget child;
  final bool enabled;
  final Future<int> Function()? installed;
  final Future<void> Function(String json)? publish;
  final Future<HomeWidgetCounters> Function()? traffic;
  final Future<DateTime?> Function(String ip)? checkedAt;

  @override
  ConsumerState<HomeWidgetManager> createState() => _HomeWidgetManagerState();
}

class _HomeWidgetManagerState extends ConsumerState<HomeWidgetManager>
    with WidgetsBindingObserver {
  final _gate = HomeWidgetPublishGate();
  Timer? _ticker;
  Timer? _pending;
  ProviderSubscription<AsyncValue<IpQuality>>? _qualitySub;
  String? _qualityIp;
  OutboundIpProbe? _probe;
  var _watching = false;
  var _generation = 0;

  @override
  void initState() {
    super.initState();
    if (!widget.enabled) {
      return;
    }
    WidgetsBinding.instance.addObserver(this);
    ref.listenManual(runTimeProvider, (_, _) => _schedule());
    ref.listenManual(coreStatusProvider, (_, _) => _schedule());
    ref.listenManual(patchClashConfigProvider, (_, _) => _schedule());
    ref.listenManual(networkSettingProvider, (_, _) => _schedule());
    ref.listenManual(groupsProvider, (_, _) => _schedule());
    ref.listenManual(currentProfileProvider, (_, _) => _schedule());
    ref.listenManual(profilesProvider, (_, _) => _schedule());
    ref.listenManual(authorizedTunEnableProvider, (_, _) => _schedule());
    ref.listenManual(loadedLocaleProvider, (_, _) => _schedule());
    ref.listenManual(suspendProvider, (_, _) => _schedule());
    unawaited(_publish(force: true));
  }

  void _schedule() {
    _pending ??= Timer(const Duration(milliseconds: 200), () {
      _pending = null;
      unawaited(_publish(force: false));
    });
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      unawaited(_publish(force: true));
    }
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _pending?.cancel();
    _qualitySub?.close();
    if (_watching) {
      _probe?.unwatch(routedOutbound);
    }
    if (widget.enabled) {
      WidgetsBinding.instance.removeObserver(this);
    }
    super.dispose();
  }

  Future<void> _publish({required bool force}) async {
    if (!widget.enabled || !mounted) {
      return;
    }
    final generation = ++_generation;
    final installed = await _installedCount();
    if (!mounted || generation != _generation) {
      return;
    }
    final running = ref.read(isStartProvider);
    final probe = homeWidgetShouldProbeIp(
      installed: installed,
      running: running,
    );
    _syncProbe(probe);
    _ensureTicker(running && installed > 0);
    final HomeWidgetFacts? facts;
    try {
      facts = await _facts(probe: probe);
    } catch (error) {
      commonPrint.log(
        'home widget snapshot failed: $error',
        logLevel: LogLevel.warning,
      );
      return;
    }
    if (facts == null || !mounted || generation != _generation) {
      return;
    }
    final snapshot = buildHomeWidgetSnapshot(facts);
    if (!force &&
        !_gate.shouldPublish(homeWidgetSignature(snapshot), facts.now)) {
      return;
    }
    await (widget.publish ?? _publishChannel)(jsonEncode(snapshot));
  }

  Future<int> _installedCount() async {
    try {
      return await (widget.installed ?? _installedChannel)();
    } catch (error) {
      commonPrint.log(
        'home widget count failed: $error',
        logLevel: LogLevel.warning,
      );
      return 0;
    }
  }

  Future<int> _installedChannel() async {
    return await homeWidgetChannel?.installed() ?? 0;
  }

  Future<void> _publishChannel(String json) async {
    await homeWidgetChannel?.publish(json);
  }

  void _ensureTicker(bool want) {
    if (want && _ticker == null) {
      _ticker = Timer.periodic(const Duration(seconds: 3), (_) {
        unawaited(_publish(force: true));
      });
      return;
    }
    if (!want) {
      _ticker?.cancel();
      _ticker = null;
    }
  }

  void _syncProbe(bool probe) {
    if (probe && !_watching) {
      final notifier = ref.read(outboundIpProbeProvider.notifier);
      _probe = notifier;
      notifier.watch(routedOutbound);
      _watching = true;
      return;
    }
    if (!probe && _watching) {
      _probe?.unwatch(routedOutbound);
      _watching = false;
    }
  }

  void _followQuality(String? ip, {required bool probe}) {
    if (!probe || ip == null) {
      _qualitySub?.close();
      _qualitySub = null;
      _qualityIp = null;
      return;
    }
    if (_qualityIp == ip) {
      return;
    }
    _qualitySub?.close();
    _qualityIp = ip;
    _qualitySub = ref.listenManual(ipQualityProvider(ip), (_, next) {
      if (!next.isLoading) {
        unawaited(_publish(force: true));
      }
    });
  }

  Future<HomeWidgetFacts?> _facts({required bool probe}) async {
    final l10n = currentAppLocalizations;
    final running = ref.read(isStartProvider);
    final profile = ref.read(currentProfileProvider);
    final entry = probe
        ? ref.read(outboundIpProbeProvider).entryOf(routedOutbound)
        : null;
    final exitIp = entry?.value?.ip;
    AsyncValue<IpQuality>? qualityState;
    if (probe && exitIp != null) {
      qualityState = ref.read(ipQualityProvider(exitIp));
    }
    _followQuality(exitIp, probe: probe && exitIp != null);
    final quality = qualityState?.asData?.value;
    final counters = running
        ? await (widget.traffic ?? _readTraffic)()
        : const HomeWidgetCounters();
    DateTime? checkedAt;
    if (quality != null) {
      checkedAt = await (widget.checkedAt ?? _checkedAt)(quality.ip);
    }
    return HomeWidgetFacts(
      now: DateTime.now(),
      l10n: l10n,
      hasProfile: ref.read(profilesProvider).isNotEmpty,
      running: running,
      suspended: ref.read(suspendProvider),
      coreStatus: ref.read(coreStatusProvider),
      mode: ref.read(patchClashConfigProvider).mode,
      groups: [
        for (final group in ref.read(groupsProvider))
          HomeWidgetGroupRef(group.name, group.now ?? ''),
      ],
      currentGroupName: profile?.currentGroupName,
      profileLabel: profile?.label,
      tunEnabled: ref.read(patchClashConfigProvider).tun.enable,
      systemProxy: ref.read(networkSettingProvider).systemProxy,
      vpnAuthorization: ref.read(authorizedTunEnableProvider),
      rate: counters.rate,
      session: counters.session,
      connections: counters.connections,
      ip: HomeWidgetIpFacts(
        exitIp: exitIp,
        quality: quality,
        checkedAt: checkedAt,
        pending:
            entry == null ||
            entry.phase == ProbePhase.empty ||
            entry.phase == ProbePhase.probing ||
            (qualityState?.isLoading ?? false),
        failed:
            (entry?.phase == ProbePhase.failed && entry?.value == null) ||
            ((qualityState?.hasError ?? false) && quality == null),
      ),
    );
  }

  Future<HomeWidgetCounters> _readTraffic() async {
    final onlyProxy = ref.read(appSettingProvider).onlyStatisticsProxy;
    final core = ref.read(coreHandlerProvider);
    Traffic? rate;
    Traffic? session;
    int? connections;
    try {
      rate = await core.getTraffic(onlyProxy);
    } catch (error) {
      commonPrint.log(
        'home widget rate failed: $error',
        logLevel: LogLevel.warning,
      );
    }
    try {
      session = await core.getTotalTraffic(onlyProxy);
    } catch (error) {
      commonPrint.log(
        'home widget session traffic failed: $error',
        logLevel: LogLevel.warning,
      );
    }
    try {
      connections = await core.getConnectionCount();
    } catch (error) {
      commonPrint.log(
        'home widget connections failed: $error',
        logLevel: LogLevel.warning,
      );
    }
    return HomeWidgetCounters(
      rate: rate,
      session: session,
      connections: connections,
    );
  }

  Future<DateTime?> _checkedAt(String ip) async {
    try {
      return (await database.ipQualitiesDao.findByIp(ip))?.checkedAt;
    } catch (error) {
      commonPrint.log(
        'home widget ip check time failed: $error',
        logLevel: LogLevel.warning,
      );
      return null;
    }
  }
}
