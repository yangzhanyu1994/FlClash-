import 'dart:convert';

import 'package:fl_clash/common/app_localizations.dart';
import 'package:fl_clash/enum/enum.dart';
import 'package:fl_clash/features/home_widget/home_widget.dart';
import 'package:fl_clash/l10n/l10n.dart';
import 'package:fl_clash/manager/home_widget_manager.dart';
import 'package:fl_clash/models/models.dart';
import 'package:fl_clash/providers/providers.dart';
import 'package:fl_clash/state.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../helpers/test_app.dart';
import '../helpers/test_profiles.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppLocalizations l10n;

  setUpAll(() {
    l10n = loadAppLocalizations(const Locale('en'));
  });

  HomeWidgetFacts facts({
    DateTime? now,
    bool hasProfile = true,
    bool running = true,
    bool suspended = false,
    CoreStatus coreStatus = CoreStatus.connected,
    Mode mode = Mode.rule,
    List<HomeWidgetGroupRef> groups = const [
      HomeWidgetGroupRef('Proxy', 'Node A'),
    ],
    String? currentGroupName = 'Proxy',
    String? profileLabel = 'Daily',
    bool tunEnabled = true,
    bool systemProxy = false,
    TunAuthorizationState vpnAuthorization = TunAuthorizationState.authorized,
    Traffic? rate = const Traffic(up: 51814, down: 2202009),
    Traffic? session = const Traffic(up: 1200, down: 2400),
    int? connections = 4,
    HomeWidgetIpFacts ip = const HomeWidgetIpFacts(),
    Duration maxAge = const Duration(days: 3),
  }) {
    return HomeWidgetFacts(
      now: now ?? DateTime.utc(2026, 11, 24, 4, 35),
      l10n: l10n,
      hasProfile: hasProfile,
      running: running,
      suspended: suspended,
      coreStatus: coreStatus,
      mode: mode,
      groups: groups,
      currentGroupName: currentGroupName,
      profileLabel: profileLabel,
      tunEnabled: tunEnabled,
      systemProxy: systemProxy,
      vpnAuthorization: vpnAuthorization,
      rate: rate,
      session: session,
      connections: connections,
      ip: ip,
      maxAge: maxAge,
    );
  }

  Map<String, Object?> snapshotOf(HomeWidgetFacts input) {
    return buildHomeWidgetSnapshot(input);
  }

  test('sizes keep a narrow widget on the compact layout', () {
    expect(
      homeWidgetSizeFor(widthDp: 140, heightDp: 320),
      HomeWidgetSize.compact,
    );
    expect(homeWidgetSizeFor(widthDp: 300, heightDp: 70), HomeWidgetSize.thin);
    expect(homeWidgetSizeFor(widthDp: 320, heightDp: 160), HomeWidgetSize.wide);
    expect(
      homeWidgetSizeFor(widthDp: 360, heightDp: 360),
      HomeWidgetSize.large,
    );
    expect(
      homeWidgetFields[HomeWidgetSize.compact]!.contains('session'),
      isFalse,
    );
    expect(homeWidgetFields[HomeWidgetSize.compact]!.contains('ip'), isFalse);
    expect(homeWidgetFields[HomeWidgetSize.wide]!.contains('session'), isFalse);
    expect(homeWidgetFields[HomeWidgetSize.wide], contains('ip'));
    expect(homeWidgetFields[HomeWidgetSize.large], contains('session'));
    expect(homeWidgetFields[HomeWidgetSize.thin], contains('rates'));
    expect(homeWidgetFields[HomeWidgetSize.thin]!.contains('ip'), isFalse);
  });

  test('a running core shows this session, not a daily total', () {
    final snapshot = snapshotOf(facts());
    expect(snapshot['run'], 'running');
    expect(snapshot['status'], l10n.running);
    expect(snapshot['node'], 'Node A');
    expect(snapshot['mode'], l10n.rule);
    expect(snapshot['upRate'], '50.6 KB/s');
    expect(snapshot['downRate'], '2.1 MB/s');
    expect(snapshot['ratesLive'], isTrue);
    expect(snapshot['sessionUp'], contains(l10n.homeWidgetSessionUpload));
    expect(snapshot['sessionDown'], contains(l10n.homeWidgetSessionDownload));
    expect(snapshot['sessionUp'], contains('1.2 KB'));
    expect(snapshot['connections'], '${l10n.connections} 4');
    expect(snapshot['profile'], '${l10n.profile} Daily');
    expect(snapshot['takeover'], contains(l10n.tun));
    expect(snapshot['core'], contains(l10n.running));
    expect(snapshot['core'], isNot(contains('verified')));
    final encoded = jsonEncode(snapshot);
    expect(encoded.contains('today'), isFalse);
    expect(encoded.contains('Today'), isFalse);
  });

  test('stopping clears rates and session totals', () {
    final snapshot = snapshotOf(
      facts(running: false, coreStatus: CoreStatus.disconnected),
    );
    expect(snapshot['run'], 'stopped');
    expect(snapshot['status'], l10n.notRunning);
    expect(snapshot['node'], homeWidgetBlank);
    expect(snapshot['upRate'], homeWidgetBlank);
    expect(snapshot['downRate'], homeWidgetBlank);
    expect(snapshot['ratesLive'], isFalse);
    expect(snapshot['sessionUp'], homeWidgetBlank);
    expect(snapshot['sessionDown'], homeWidgetBlank);
    expect(snapshot['connections'], homeWidgetBlank);
  });

  test('connecting, failure, and a missing profile stay distinct', () {
    expect(
      snapshotOf(facts(coreStatus: CoreStatus.connecting))['status'],
      l10n.connecting,
    );
    expect(
      snapshotOf(
        facts(running: true, coreStatus: CoreStatus.disconnected),
      )['run'],
      'error',
    );
    expect(
      snapshotOf(
        facts(
          running: false,
          coreStatus: CoreStatus.disconnected,
          hasProfile: false,
        ),
      )['node'],
      l10n.homeWidgetNoProfile,
    );
    final suspended = snapshotOf(facts(suspended: true));
    expect(suspended['status'], l10n.suspended);
    expect(suspended['ratesLive'], isFalse);
  });

  test('grades reuse IpQuality.level and drop a result from another exit', () {
    const exitIp = '203.0.113.10';
    const otherIp = '203.0.113.11';
    const residential = IpQuality(
      ip: exitIp,
      source: IpQualitySource.identMe,
      type: IpType.residential,
      isVpn: true,
    );
    const tor = IpQuality(
      ip: exitIp,
      source: IpQualitySource.ipApiIs,
      type: IpType.residential,
      isTor: true,
    );
    const hosting = IpQuality(
      ip: exitIp,
      source: IpQualitySource.proxyCheck,
      type: IpType.hosting,
      isProxy: true,
    );
    expect(residential.level, IpQualityLevel.good);
    expect(tor.level, IpQualityLevel.risky);
    expect(hosting.level, IpQualityLevel.normal);

    final ready = snapshotOf(
      facts(
        ip: HomeWidgetIpFacts(
          exitIp: exitIp,
          quality: residential,
          checkedAt: DateTime.utc(2026, 11, 24, 4, 30),
        ),
      ),
    );
    expect(ready['ipType'], l10n.ipTypeResidential);
    expect(ready['ipLevel'], l10n.ipQualityGood);
    expect(ready['ipLevelTone'], 'ok');
    expect(ready['ipMeta'], contains(IpQualitySource.identMe.label));
    expect(jsonEncode(ready).contains(exitIp), isFalse);

    final risky = snapshotOf(
      facts(
        ip: HomeWidgetIpFacts(
          exitIp: exitIp,
          quality: tor,
          checkedAt: DateTime.utc(2026, 11, 24, 4, 30),
        ),
      ),
    );
    expect(risky['ipLevel'], l10n.ipQualityRisky);
    expect(risky['ipLevelTone'], 'error');

    final moved = snapshotOf(
      facts(
        ip: HomeWidgetIpFacts(
          exitIp: otherIp,
          quality: residential,
          checkedAt: DateTime.utc(2026, 11, 24, 4, 30),
          pending: true,
        ),
      ),
    );
    expect(moved['ipType'], homeWidgetBlank);
    expect(moved['ipLevel'], homeWidgetBlank);
    expect(moved['ipState'], l10n.servicePending);
    expect(jsonEncode(moved).contains(exitIp), isFalse);
    expect(jsonEncode(moved).contains(otherIp), isFalse);
  });

  test('an old check is expired and a refresh in flight is partial', () {
    const exitIp = '203.0.113.10';
    const quality = IpQuality(
      ip: exitIp,
      source: IpQualitySource.ipLocate,
      type: IpType.mobile,
    );
    final expired = snapshotOf(
      facts(
        now: DateTime.utc(2026, 11, 24, 4, 35),
        ip: HomeWidgetIpFacts(
          exitIp: exitIp,
          quality: quality,
          checkedAt: DateTime.utc(2026, 11, 20, 4, 35),
        ),
      ),
    );
    expect(expired['ipState'], l10n.homeWidgetIpExpired);
    expect(expired['ipLevel'], homeWidgetBlank);
    expect(expired['ipMeta'], contains('iplocate.io'));

    final partial = snapshotOf(
      facts(
        ip: HomeWidgetIpFacts(
          exitIp: exitIp,
          quality: quality,
          checkedAt: DateTime.utc(2026, 11, 24, 4, 30),
          pending: true,
        ),
      ),
    );
    expect(partial['ipState'], l10n.homeWidgetIpPartial);
    expect(partial['ipType'], l10n.ipTypeMobile);
  });

  test('a stopped or quiet process does not keep rates looking live', () {
    final fresh = snapshotOf(facts());
    final later = presentHomeWidget(
      fresh,
      DateTime.utc(2026, 11, 24, 4, 35, 21),
    );
    expect(later['upRate'], homeWidgetBlank);
    expect(later['ratesLive'], isFalse);
    expect(later['status'], l10n.running);

    final stale = presentHomeWidget(fresh, DateTime.utc(2026, 11, 24, 4, 39));
    expect(stale['status'], l10n.homeWidgetStale);
    expect(stale['node'], contains('Node A'));
    expect(stale['upRate'], homeWidgetBlank);

    final stopped = presentHomeWidget(
      snapshotOf(facts(running: false, coreStatus: CoreStatus.disconnected)),
      DateTime.utc(2026, 11, 24, 4, 35, 1),
    );
    expect(stopped['upRate'], homeWidgetBlank);
    expect(stopped['status'], l10n.notRunning);
  });

  test('the open action does not start or stop the service', () {
    expect(homeWidgetStartsService(homeWidgetOpenAction), isFalse);
    expect(homeWidgetStartsService('com.follow.clash.action.START'), isTrue);
    expect(homeWidgetStartsService('com.follow.clash.action.STOP'), isTrue);
    expect(homeWidgetStartsService('com.follow.clash.action.TOGGLE'), isTrue);
    expect(
      homeWidgetStartsService(
        'com.follow.clash.intent.action.VPN_START_REQUESTED',
      ),
      isTrue,
    );
    expect(homeWidgetShouldProbeIp(installed: 0, running: true), isFalse);
    expect(homeWidgetShouldProbeIp(installed: 2, running: false), isFalse);
    expect(homeWidgetShouldProbeIp(installed: 2, running: true), isTrue);
  });

  test('identical snapshots inside the quiet window publish once', () {
    final gate = HomeWidgetPublishGate();
    final now = DateTime.utc(2026, 11, 24, 4, 35);
    expect(gate.shouldPublish('same', now), isTrue);
    expect(
      gate.shouldPublish('same', now.add(const Duration(seconds: 1))),
      isFalse,
    );
    expect(
      gate.shouldPublish('changed', now.add(const Duration(seconds: 1))),
      isTrue,
    );
    expect(
      gate.shouldPublish(
        'changed',
        now.add(const Duration(seconds: 1)),
        force: true,
      ),
      isTrue,
    );
    expect(
      gate.shouldPublish('changed', now.add(const Duration(seconds: 4))),
      isTrue,
    );
  });

  test('direct mode names the direct outbound', () {
    final snapshot = snapshotOf(facts(mode: Mode.direct, groups: const []));
    expect(snapshot['node'], l10n.direct);
    expect(snapshot['mode'], l10n.direct);
  });

  test('vpn permission is reported without starting the tunnel', () {
    final snapshot = snapshotOf(
      facts(
        running: false,
        coreStatus: CoreStatus.disconnected,
        tunEnabled: true,
        vpnAuthorization: TunAuthorizationState.unauthorized,
      ),
    );
    expect(snapshot['detail'], l10n.homeWidgetVpnNeeded);
    expect(snapshot['takeover'], l10n.homeWidgetVpnNeeded);
    expect(snapshot['run'], 'stopped');
    expect(homeWidgetStartsService(homeWidgetOpenAction), isFalse);
  });

  testWidgets('the manager publishes status and never a service action', (
    tester,
  ) async {
    final payloads = <String>[];
    final container = ProviderContainer(
      overrides: [
        profilesProvider.overrideWith(TestProfiles.new),
        suspendProvider.overrideWithValue(false),
      ],
    );
    globalState.container = container;
    addTearDown(container.dispose);

    Future<HomeWidgetCounters> traffic() async {
      return const HomeWidgetCounters(
        rate: Traffic(up: 100, down: 200),
        session: Traffic(up: 300, down: 400),
        connections: 2,
      );
    }

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: HostApp(
          home: HomeWidgetManager(
            key: const ValueKey('off'),
            enabled: false,
            installed: () async => 0,
            publish: (json) async {
              payloads.add(json);
            },
            traffic: traffic,
            child: const SizedBox.shrink(),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(payloads, isEmpty);

    container.read(runTimeProvider.notifier).value = 1;
    container.read(coreStatusProvider.notifier).value = CoreStatus.connected;
    container.read(groupsProvider.notifier).value = [
      const Group(type: GroupType.Selector, name: 'GLOBAL', now: 'Node A'),
    ];
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: HostApp(
          home: HomeWidgetManager(
            key: const ValueKey('on'),
            enabled: true,
            installed: () async => 0,
            publish: (json) async {
              payloads.add(json);
            },
            traffic: traffic,
            checkedAt: (_) async => null,
            child: const SizedBox.shrink(),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(payloads, isNotEmpty);
    final running = jsonDecode(payloads.last) as Map<String, Object?>;
    expect(running['run'], 'running');
    expect(running['node'], 'Node A');
    expect(running['ratesLive'], isTrue);
    expect(running['upRate'], isNot(homeWidgetBlank));
    for (final payload in payloads) {
      expect(payload.contains('.action.START'), isFalse);
      expect(payload.contains('.action.STOP'), isFalse);
      expect(payload.contains('.action.TOGGLE'), isFalse);
      expect(homeWidgetStartsService(payload), isFalse);
    }

    container.read(runTimeProvider.notifier).value = null;
    container.read(coreStatusProvider.notifier).value = CoreStatus.disconnected;
    await tester.pump(const Duration(milliseconds: 250));
    await tester.pump();
    final stopped = jsonDecode(payloads.last) as Map<String, Object?>;
    expect(stopped['run'], 'stopped');
    expect(stopped['upRate'], homeWidgetBlank);
    expect(stopped['sessionUp'], homeWidgetBlank);
    expect(stopped['ratesLive'], isFalse);
  });
}
