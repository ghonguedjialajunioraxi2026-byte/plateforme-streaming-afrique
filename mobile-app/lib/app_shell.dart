import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/theme/flick_colors.dart';
import 'features/auth/auth_controller.dart';
import 'features/explore/explore_screen.dart';
import 'features/home/home_screen.dart';
import 'features/notifications/notifications_screen.dart';
import 'features/profile/profile_screen.dart';
import 'features/social/social_repository.dart';
import 'shared/widgets/widgets.dart';

/// Coquille mobile : bottom navigation (Accueil, Explorer, Créer,
/// Notifications, Profil). « Créer » ouvre une feuille d'action —
/// le streaming reste OBS/PC en priorité (spectateurs d'abord).
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _index = 0;
  int _unread = 0;

  static const _pages = [
    HomeScreen(),
    ExploreScreen(),
    NotificationsScreen(),
    ProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    _loadUnread();
  }

  Future<void> _loadUnread() async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) return;
    try {
      final n = await context
          .read<NotificationsRepository>()
          .unreadCount(recipientId: auth.user?.id);
      if (mounted) setState(() => _unread = n);
    } catch (_) {}
  }

  void _onTap(int i) {
    if (i == 2) {
      _openCreate();
      return;
    }
    setState(() => _index = i > 2 ? i - 1 : i);
  }

  void _openCreate() {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.videocam, color: FlickColors.green),
                  SizedBox(width: 8),
                  Text('Passer en direct',
                      style:
                          TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                ],
              ),
              const SizedBox(height: 12),
              const Text(
                'FLICK mobile est d\u2019abord une expérience spectateur. '
                'Lancez vos lives depuis OBS sur PC, puis retrouvez-les ici.',
              ),
              const SizedBox(height: 16),
              FlickButton(
                label: 'Voir l\u2019assistant',
                secondary: true,
                onPressed: () {
                  Navigator.of(context).pop();
                  Navigator.of(context).pushNamed('/assistant');
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tab = _index > 1 ? _index + 1 : _index;
    return Scaffold(
      appBar: FlickAppBar(
        unread: _unread,
        onSearch: () => Navigator.of(context).pushNamed('/search'),
        onNotifications: () => setState(() => _index = 2),
        avatar: FlickAvatar(
            name: context.watch<AuthController>().user?.displayName),
        onAvatar: () => setState(() => _index = 3),
      ),
      body: IndexedStack(index: _index, children: _pages),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: tab,
        onTap: _onTap,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Accueil'),
          BottomNavigationBarItem(
              icon: Icon(Icons.explore), label: 'Explorer'),
          BottomNavigationBarItem(
              icon: Icon(Icons.add_circle, size: 32), label: 'Créer'),
          BottomNavigationBarItem(
              icon: Icon(Icons.notifications), label: 'Alertes'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profil'),
        ],
      ),
    );
  }
}
