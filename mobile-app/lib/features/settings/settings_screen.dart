import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/storage/app_prefs.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/widgets/widgets.dart';

/// Paramètres : économie de données, langue, pays, devise d'affichage.
/// La devise ne sert qu'à l'affichage — le serveur reste la vérité.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const levels = {
    'auto': 'Auto (recommandé)',
    'eco': 'Économie (réseaux lents)',
    'standard': 'Standard',
    'high': 'Haute qualité',
  };
  static const locales = {'fr': 'Français', 'en': 'English'};

  @override
  Widget build(BuildContext context) {
    final prefs = context.watch<AppPrefs>();
    return Scaffold(
      appBar: AppBar(title: const Text('Paramètres')),
      body: ListView(
        padding: const EdgeInsets.all(FlickSpacing.md),
        children: [
          const FlickSectionHeader(title: 'Économie de données'),
          const SizedBox(height: FlickSpacing.sm),
          FlickCard(
            child: RadioGroup<String>(
              groupValue: prefs.dataSaver,
              onChanged: (v) async {
                if (v == null) return;
                await context.read<AppPrefs>().setDataSaver(v);
                setState(() {});
              },
              child: Column(
                children: levels.entries.map((e) {
                  final selected = prefs.dataSaver == e.key;
                  return InkWell(
                    onTap: () async {
                      await context.read<AppPrefs>().setDataSaver(e.key);
                      setState(() {});
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Row(
                        children: [
                          Radio<String>(value: e.key),
                          Expanded(
                            child: Text(e.value,
                                style: FlickTypography.bodySmall),
                          ),
                          if (selected)
                            const Icon(Icons.check_circle,
                                color: FlickColors.green),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
          const Text(
            'Éco réduit la qualité vidéo et désactive le préchargement. '
            'La qualité maximale n\u2019est jamais forcée.',
            style: FlickTypography.caption,
          ),
          const SizedBox(height: FlickSpacing.lg),
          const FlickSectionHeader(title: 'Langue'),
          const SizedBox(height: FlickSpacing.sm),
          FlickCard(
            child: RadioGroup<String>(
              groupValue: prefs.localeCode,
              onChanged: (v) async {
                if (v == null) return;
                await context.read<AppPrefs>().setLocaleCode(v);
                setState(() {});
              },
              child: Column(
                children: locales.entries.map((e) {
                  return InkWell(
                    onTap: () async {
                      await context.read<AppPrefs>().setLocaleCode(e.key);
                      setState(() {});
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Row(
                        children: [
                          Radio<String>(value: e.key),
                          Expanded(
                            child: Text(e.value,
                                style: FlickTypography.bodySmall),
                          ),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
          const SizedBox(height: FlickSpacing.lg),
          const FlickSectionHeader(title: 'Région (Africa+)'),
          const SizedBox(height: FlickSpacing.sm),
          FlickCard(
            child: Column(
              children: [
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Pays', style: FlickTypography.bodySmall),
                  trailing: Text(prefs.country,
                      style: FlickTypography.bodySmall.copyWith(
                          color: FlickColors.green)),
                  onTap: () => _pickCountry(context, prefs),
                ),
                const Divider(height: 1),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Devise d\u2019affichage',
                      style: FlickTypography.bodySmall),
                  trailing: Text(prefs.currency,
                      style: FlickTypography.bodySmall.copyWith(
                          color: FlickColors.green)),
                  onTap: () => _pickCurrency(context, prefs),
                ),
              ],
            ),
          ),
          const Text(
            'Aucune localisation GPS demandée. Aucune conversion monétaire '
            'côté mobile : les montants viennent du serveur.',
            style: FlickTypography.caption,
          ),
        ],
      ),
    );
  }

  Future<void> _pickCountry(BuildContext context, AppPrefs prefs) async {
    final controller = TextEditingController(text: prefs.country);
    final code = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Code pays (ISO)'),
        content: TextField(
            controller: controller,
            textCapitalization: TextCapitalization.characters,
            maxLength: 2),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Annuler')),
          TextButton(
              onPressed: () =>
                  Navigator.of(context).pop(controller.text.trim().toUpperCase()),
              child: const Text('OK')),
        ],
      ),
    );
    if (code != null && code.isNotEmpty && context.mounted) {
      await context.read<AppPrefs>().setCountry(code);
      setState(() {});
    }
  }

  Future<void> _pickCurrency(BuildContext context, AppPrefs prefs) async {
    const options = ['XAF', 'XOF', 'GHS', 'NGN', 'KES', 'ZAR', 'EUR', 'USD'];
    final code = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Devise d\u2019affichage'),
        children: options
            .map((c) => SimpleDialogOption(
                  onPressed: () => Navigator.of(context).pop(c),
                  child: Text(c),
                ))
            .toList(),
      ),
    );
    if (code != null && context.mounted) {
      await context.read<AppPrefs>().setCurrency(code);
      setState(() {});
    }
  }
}
