import 'dart:convert';

import 'package:fl_clash/common/common.dart';
import 'package:fl_clash/enum/enum.dart';
import 'package:fl_clash/l10n/l10n.dart';
import 'package:fl_clash/models/models.dart';

const homeWidgetBlank = '—';

const homeWidgetOpenAction = 'android.intent.action.MAIN';

const homeWidgetRateStale = Duration(seconds: 20);

const homeWidgetStateStale = Duration(minutes: 3);

const homeWidgetWideMinDp = 250.0;

const homeWidgetThinMaxHeightDp = 90.0;

const homeWidgetLargeMinHeightDp = 280.0;

enum HomeWidgetSize { thin, compact, wide, large }

enum HomeWidgetRun { running, stopped, connecting, error, unknown }

const homeWidgetFields = <HomeWidgetSize, Set<String>>{
  HomeWidgetSize.thin: {'status', 'node', 'rates'},
  HomeWidgetSize.compact: {'status', 'node'},
  HomeWidgetSize.wide: {'status', 'node', 'mode', 'rates', 'ip'},
  HomeWidgetSize.large: {
    'status',
    'node',
    'mode',
    'rates',
    'ip',
    'session',
    'connections',
    'profile',
    'takeover',
    'core',
  },
};

class HomeWidgetGroupRef {
  const HomeWidgetGroupRef(this.name, this.now);

  final String name;
  final String now;
}

class HomeWidgetIpFacts {
  const HomeWidgetIpFacts({
    this.exitIp,
    this.quality,
    this.checkedAt,
    this.pending = false,
    this.failed = false,
  });

  final String? exitIp;
  final IpQuality? quality;
  final DateTime? checkedAt;
  final bool pending;
  final bool failed;
}

class HomeWidgetFacts {
  const HomeWidgetFacts({
    required this.now,
    required this.l10n,
    required this.hasProfile,
    required this.running,
    required this.suspended,
    required this.coreStatus,
    required this.mode,
    required this.groups,
    required this.tunEnabled,
    required this.systemProxy,
    required this.vpnAuthorization,
    this.currentGroupName,
    this.profileLabel,
    this.rate,
    this.session,
    this.connections,
    this.ip = const HomeWidgetIpFacts(),
    this.maxAge = const Duration(days: 3),
  });

  final DateTime now;
  final AppLocalizations l10n;
  final bool hasProfile;
  final bool running;
  final bool suspended;
  final CoreStatus coreStatus;
  final Mode mode;
  final List<HomeWidgetGroupRef> groups;
  final String? currentGroupName;
  final String? profileLabel;
  final bool tunEnabled;
  final bool systemProxy;
  final TunAuthorizationState vpnAuthorization;
  final Traffic? rate;
  final Traffic? session;
  final int? connections;
  final HomeWidgetIpFacts ip;
  final Duration maxAge;
}

class HomeWidgetPublishGate {
  String? _signature;
  DateTime? _at;

  bool shouldPublish(String signature, DateTime now, {bool force = false}) {
    final recent =
        _at != null && now.difference(_at!) < const Duration(seconds: 2);
    if (!force && signature == _signature && recent) {
      return false;
    }
    _signature = signature;
    _at = now;
    return true;
  }
}

bool homeWidgetStartsService(String? action) {
  if (action == null || action.isEmpty) {
    return false;
  }
  return action.endsWith('.action.START') ||
      action.endsWith('.action.STOP') ||
      action.endsWith('.action.TOGGLE') ||
      action.endsWith('.intent.action.VPN_START_REQUESTED') ||
      action.endsWith('.intent.action.VPN_REVOKED');
}

bool homeWidgetShouldProbeIp({required int installed, required bool running}) {
  return installed > 0 && running;
}

HomeWidgetSize homeWidgetSizeFor({
  required double widthDp,
  required double heightDp,
}) {
  final wide = widthDp >= homeWidgetWideMinDp;
  if (wide && heightDp < homeWidgetThinMaxHeightDp) {
    return HomeWidgetSize.thin;
  }
  if (!wide) {
    return HomeWidgetSize.compact;
  }
  if (heightDp < homeWidgetLargeMinHeightDp) {
    return HomeWidgetSize.wide;
  }
  return HomeWidgetSize.large;
}

HomeWidgetRun homeWidgetRun({
  required bool running,
  required bool suspended,
  required CoreStatus coreStatus,
}) {
  if (running && suspended) {
    return HomeWidgetRun.connecting;
  }
  if (running && coreStatus == CoreStatus.connected) {
    return HomeWidgetRun.running;
  }
  if (running && coreStatus == CoreStatus.connecting) {
    return HomeWidgetRun.connecting;
  }
  if (running && coreStatus == CoreStatus.disconnected) {
    return HomeWidgetRun.error;
  }
  if (!running && coreStatus == CoreStatus.disconnected) {
    return HomeWidgetRun.stopped;
  }
  return HomeWidgetRun.error;
}

String? resolveHomeWidgetNode({
  required Mode mode,
  required String? currentGroupName,
  required List<HomeWidgetGroupRef> groups,
}) {
  if (mode == Mode.direct) {
    return null;
  }
  String? nowOf(String name) {
    for (final group in groups) {
      if (group.name == name && group.now.isNotEmpty) {
        return group.now;
      }
    }
    return null;
  }

  if (mode == Mode.global) {
    return nowOf('GLOBAL') ?? _firstNow(groups);
  }
  final current = currentGroupName == null ? null : nowOf(currentGroupName);
  return current ?? nowOf('GLOBAL') ?? _firstNow(groups);
}

String? _firstNow(List<HomeWidgetGroupRef> groups) {
  for (final group in groups) {
    if (group.now.isNotEmpty) {
      return group.now;
    }
  }
  return null;
}

String homeWidgetSignature(Map<String, Object?> snapshot) {
  final copy = Map<String, Object?>.of(snapshot)..['updatedAt'] = 0;
  return jsonEncode(copy);
}

Map<String, Object?> buildHomeWidgetSnapshot(HomeWidgetFacts facts) {
  final l10n = facts.l10n;
  final run = homeWidgetRun(
    running: facts.running,
    suspended: facts.suspended,
    coreStatus: facts.coreStatus,
  );
  final live = run == HomeWidgetRun.running;
  final nodeName = resolveHomeWidgetNode(
    mode: facts.mode,
    currentGroupName: facts.currentGroupName,
    groups: facts.groups,
  );
  final shownNode = _nodeLabel(facts, run, nodeName);
  final staleNode = nodeName == null
      ? homeWidgetBlank
      : l10n.homeWidgetLastNode(
          facts.mode == Mode.direct ? l10n.direct : nodeName,
        );
  final quality = _matchingQuality(facts.ip);
  final ipState = _ipState(facts, run, quality);
  final level = quality?.level;
  final checked = facts.ip.checkedAt;
  final expired =
      checked != null && facts.now.difference(checked) > facts.maxAge;
  final showQuality =
      quality != null &&
      (ipState == _IpView.ready ||
          ipState == _IpView.partial ||
          ipState == _IpView.expired);
  final time = checked == null ? '' : _clock(checked);
  final source = showQuality ? quality.source.label : '';
  final meta = [
    if (source.isNotEmpty) source,
    if (time.isNotEmpty) time,
  ].join(' · ');
  final rate = live ? facts.rate : null;
  final session = live ? facts.session : null;
  return {
    'updatedAt': facts.now.millisecondsSinceEpoch,
    'run': run.name,
    'status': _status(l10n, facts, run),
    'statusTone': _statusTone(run),
    'staleLabel': l10n.homeWidgetStale,
    'staleNode': staleNode,
    'node': shownNode,
    'mode': live ? _modeLabel(l10n, facts.mode) : homeWidgetBlank,
    'upRate': rate == null ? homeWidgetBlank : _rate(rate.up),
    'downRate': rate == null ? homeWidgetBlank : _rate(rate.down),
    'ratesLive': rate != null,
    'ipType': showQuality ? _typeLabel(l10n, quality.type) : homeWidgetBlank,
    'ipLevel': showQuality && level != null && !expired
        ? levelLabel(l10n, level)
        : homeWidgetBlank,
    'ipLevelTone': level == null || !showQuality || expired
        ? 'idle'
        : _levelTone(level),
    'ipState': _ipStateLabel(l10n, ipState),
    'ipMeta': meta.isEmpty ? homeWidgetBlank : meta,
    'sessionUp': session == null
        ? homeWidgetBlank
        : '${l10n.homeWidgetSessionUpload} ${_bytes(session.up)}',
    'sessionDown': session == null
        ? homeWidgetBlank
        : '${l10n.homeWidgetSessionDownload} ${_bytes(session.down)}',
    'connections': live && facts.connections != null
        ? '${l10n.connections} ${facts.connections}'
        : homeWidgetBlank,
    'profile': _profile(l10n, facts, live),
    'takeover': _takeover(l10n, facts, live),
    'core': '${l10n.coreStatus} ${_core(l10n, facts, run)}',
    'coreTone': _statusTone(run),
    'detail': _detail(facts),
    'readOnly': l10n.homeWidgetReadOnly,
    'blank': homeWidgetBlank,
  };
}

Map<String, Object?> presentHomeWidget(
  Map<String, Object?> snapshot,
  DateTime now,
) {
  final next = Map<String, Object?>.of(snapshot);
  final updatedAt = snapshot['updatedAt'];
  final at = updatedAt is int
      ? DateTime.fromMillisecondsSinceEpoch(updatedAt)
      : DateTime.fromMillisecondsSinceEpoch(0);
  final age = now.difference(at);
  void clearRates() {
    next['upRate'] = snapshot['blank'] ?? homeWidgetBlank;
    next['downRate'] = snapshot['blank'] ?? homeWidgetBlank;
    next['ratesLive'] = false;
  }

  if (snapshot['run'] != HomeWidgetRun.running.name) {
    clearRates();
    return next;
  }
  if (age >= homeWidgetStateStale) {
    next['status'] = snapshot['staleLabel'];
    next['statusTone'] = 'warn';
    next['node'] = snapshot['staleNode'];
    next['coreTone'] = 'warn';
    clearRates();
    return next;
  }
  if (age >= homeWidgetRateStale) {
    clearRates();
  }
  return next;
}

String _modeLabel(AppLocalizations l10n, Mode mode) {
  return switch (mode) {
    Mode.rule => l10n.rule,
    Mode.global => l10n.global,
    Mode.direct => l10n.direct,
  };
}

String levelLabel(AppLocalizations l10n, IpQualityLevel level) {
  return switch (level) {
    IpQualityLevel.good => l10n.ipQualityGood,
    IpQualityLevel.normal => l10n.ipQualityNormal,
    IpQualityLevel.risky => l10n.ipQualityRisky,
  };
}

String _typeLabel(AppLocalizations l10n, IpType type) {
  return switch (type) {
    IpType.residential => l10n.ipTypeResidential,
    IpType.mobile => l10n.ipTypeMobile,
    IpType.business => l10n.ipTypeBusiness,
    IpType.hosting => l10n.ipTypeHosting,
  };
}

String _clock(DateTime time) {
  final local = time.toLocal();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}

String _rate(num bytesPerSecond) {
  final show = bytesPerSecond.traffic;
  return '${show.value} ${show.unit.trim()}/s';
}

String _bytes(num bytes) {
  final show = bytes.traffic;
  return '${show.value} ${show.unit.trim()}';
}

String _status(
  AppLocalizations l10n,
  HomeWidgetFacts facts,
  HomeWidgetRun run,
) {
  if (facts.running && facts.suspended) {
    return l10n.suspended;
  }
  return switch (run) {
    HomeWidgetRun.running => l10n.running,
    HomeWidgetRun.stopped => l10n.notRunning,
    HomeWidgetRun.connecting => l10n.connecting,
    HomeWidgetRun.error => l10n.homeWidgetAbnormal,
    HomeWidgetRun.unknown => l10n.homeWidgetAbnormal,
  };
}

String _statusTone(HomeWidgetRun run) {
  return switch (run) {
    HomeWidgetRun.running => 'ok',
    HomeWidgetRun.stopped => 'idle',
    HomeWidgetRun.connecting => 'warn',
    HomeWidgetRun.error || HomeWidgetRun.unknown => 'error',
  };
}

String _core(AppLocalizations l10n, HomeWidgetFacts facts, HomeWidgetRun run) {
  if (facts.running && facts.suspended) {
    return l10n.suspended;
  }
  return switch (run) {
    HomeWidgetRun.running => l10n.running,
    HomeWidgetRun.connecting => l10n.connecting,
    HomeWidgetRun.stopped => l10n.notRunning,
    HomeWidgetRun.error || HomeWidgetRun.unknown => l10n.homeWidgetAbnormal,
  };
}

String _nodeLabel(HomeWidgetFacts facts, HomeWidgetRun run, String? nodeName) {
  if (run != HomeWidgetRun.running && run != HomeWidgetRun.connecting) {
    if (!facts.hasProfile) {
      return facts.l10n.homeWidgetNoProfile;
    }
    return homeWidgetBlank;
  }
  if (facts.mode == Mode.direct) {
    return facts.l10n.direct;
  }
  if (nodeName == null || nodeName.isEmpty) {
    return homeWidgetBlank;
  }
  return nodeName;
}

IpQuality? _matchingQuality(HomeWidgetIpFacts ip) {
  final quality = ip.quality;
  final exitIp = ip.exitIp;
  if (quality == null || exitIp == null || quality.ip != exitIp) {
    return null;
  }
  return quality;
}

enum _IpView { hidden, pending, ready, expired, unknown, failed, partial }

_IpView _ipState(HomeWidgetFacts facts, HomeWidgetRun run, IpQuality? quality) {
  if (run != HomeWidgetRun.running) {
    return _IpView.hidden;
  }
  final checked = facts.ip.checkedAt;
  final expired =
      quality != null &&
      checked != null &&
      facts.now.difference(checked) > facts.maxAge;
  if (expired) {
    return _IpView.expired;
  }
  if (facts.ip.pending && quality != null) {
    return _IpView.partial;
  }
  if (quality != null) {
    return _IpView.ready;
  }
  if (facts.ip.pending || facts.ip.exitIp == null) {
    return _IpView.pending;
  }
  if (facts.ip.failed) {
    return _IpView.failed;
  }
  return _IpView.unknown;
}

String _ipStateLabel(AppLocalizations l10n, _IpView state) {
  return switch (state) {
    _IpView.hidden => homeWidgetBlank,
    _IpView.pending => l10n.servicePending,
    _IpView.ready => homeWidgetBlank,
    _IpView.expired => l10n.homeWidgetIpExpired,
    _IpView.unknown => l10n.ipTypeUnknown,
    _IpView.failed => l10n.serviceFailed,
    _IpView.partial => l10n.homeWidgetIpPartial,
  };
}

String _levelTone(IpQualityLevel level) {
  return switch (level) {
    IpQualityLevel.good => 'ok',
    IpQualityLevel.normal => 'idle',
    IpQualityLevel.risky => 'error',
  };
}

String _profile(AppLocalizations l10n, HomeWidgetFacts facts, bool live) {
  final label = facts.profileLabel;
  if (!live || label == null || label.isEmpty) {
    return homeWidgetBlank;
  }
  return '${l10n.profile} $label';
}

String _takeover(AppLocalizations l10n, HomeWidgetFacts facts, bool live) {
  if (_vpnBlocked(facts)) {
    return l10n.homeWidgetVpnNeeded;
  }
  if (!live) {
    return homeWidgetBlank;
  }
  if (facts.tunEnabled) {
    return '${l10n.tun} ${l10n.running}';
  }
  if (facts.systemProxy) {
    return '${l10n.systemProxy} ${l10n.running}';
  }
  return l10n.disabled;
}

String _detail(HomeWidgetFacts facts) {
  if (_vpnBlocked(facts)) {
    return facts.l10n.homeWidgetVpnNeeded;
  }
  return '';
}

bool _vpnBlocked(HomeWidgetFacts facts) {
  return facts.tunEnabled &&
      facts.vpnAuthorization == TunAuthorizationState.unauthorized &&
      !facts.running;
}
