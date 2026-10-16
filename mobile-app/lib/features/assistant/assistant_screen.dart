import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/constants/api_paths.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/widgets/widgets.dart';

/// Assistant IA — utilise UNIQUEMENT POST /api/ai/assistant du backend.
/// Jamais de clé fournisseur côté mobile (ni dans Git).
class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key});

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen> {
  final _controller = TextEditingController();
  final _items = <({String role, String text})>[];
  bool _sending = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final prompt = _controller.text.trim();
    if (prompt.isEmpty || _sending) return;
    setState(() {
      _items.add((role: 'user', text: prompt));
      _sending = true;
    });
    _controller.clear();
    try {
      final answer = await context.read<ApiClient>().post<String>(
        ApiPaths.aiAssistant,
        (j) {
          if (j is String) return j;
          final map = (j as Map<String, dynamic>?) ?? {};
          return (map['answer'] ?? map['text'] ?? map['message'] ?? '')
              .toString();
        },
        body: {'prompt': prompt},
      );
      if (mounted) {
        setState(() => _items.add((role: 'ai', text: answer)));
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() =>
            _items.add((role: 'ai', text: 'Erreur : ${e.message}')));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Assistant FLICK')),
      body: Column(
        children: [
          Expanded(
            child: _items.isEmpty
                ? const FlickEmptyState(
                    icon: Icons.smart_toy_outlined,
                    message: 'Posez votre question à l\u2019assistant FLICK')
                : ListView.builder(
                    padding: const EdgeInsets.all(FlickSpacing.md),
                    itemCount: _items.length,
                    itemBuilder: (_, i) {
                      final m = _items[i];
                      final mine = m.role == 'user';
                      return Align(
                        alignment: mine
                            ? Alignment.centerRight
                            : Alignment.centerLeft,
                        child: Container(
                          margin: const EdgeInsets.only(bottom: 8),
                          padding: const EdgeInsets.all(FlickSpacing.md),
                          constraints: BoxConstraints(
                              maxWidth:
                                  MediaQuery.of(context).size.width * 0.8),
                          decoration: BoxDecoration(
                            color: mine
                                ? FlickColors.green.withValues(alpha: 0.15)
                                : FlickColors.surface,
                            borderRadius:
                                BorderRadius.circular(FlickRadius.md),
                            border: Border.all(color: FlickColors.border),
                          ),
                          child: Text(m.text,
                              style: FlickTypography.bodySmall),
                        ),
                      );
                    },
                  ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(FlickSpacing.sm),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _send(),
                    decoration: const InputDecoration(
                        hintText: 'Votre question…',
                        isDense: true,
                        contentPadding: EdgeInsets.symmetric(
                            horizontal: 12, vertical: 10)),
                  ),
                ),
                const SizedBox(width: FlickSpacing.sm),
                IconButton.filled(
                  onPressed: _sending ? null : _send,
                  icon: _sending
                      ? const SizedBox(
                          width: 18, height: 18,
                          child:
                              CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.send, size: 20),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
