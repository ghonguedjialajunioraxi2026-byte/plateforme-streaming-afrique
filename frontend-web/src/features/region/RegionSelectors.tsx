import { useI18n, supportedLanguages } from '../../i18n'

/// Sélecteurs Africa+ (étape 19) : langue, pays, devise.
/// Accessibles (labels, clavier natif) et compacts.

interface SelectProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  help?: string
}

function Field({ id, label, value, onChange, help, children }: SelectProps & { children: React.ReactNode }) {
  return (
    <label className="block text-sm" htmlFor={id}>
      <span className="mb-1 block font-medium text-text-primary">{label}</span>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary"
      >
        {children}
      </select>
      {help && <span className="mt-1 block text-xs text-text-secondary">{help}</span>}
    </label>
  )
}

export function LanguageSelector({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { t } = useI18n()
  return (
    <Field id="language" label={t('settings.language')} value={value} onChange={onChange} help={t('settings.languageHelp')}>
      {supportedLanguages().map((lang) => (
        <option key={lang} value={lang}>
          {lang === 'fr' ? 'Français' : 'English'}
        </option>
      ))}
    </Field>
  )
}

export function CountrySelector({
  countries,
  value,
  onChange,
  includeAll = false,
}: {
  countries: { code: string; name: string }[]
  value: string
  onChange: (value: string) => void
  includeAll?: boolean
}) {
  const { t } = useI18n()
  return (
    <Field id="country" label={t('settings.country')} value={value} onChange={onChange} help={includeAll ? undefined : t('settings.countryHelp')}>
      {includeAll && <option value="">{t('common.all')}</option>}
      {!includeAll && <option value="">—</option>}
      {countries.map((country) => (
        <option key={country.code} value={country.code}>
          {country.name}
        </option>
      ))}
    </Field>
  )
}

export function CurrencySelector({
  currencies,
  value,
  onChange,
}: {
  currencies: string[]
  value: string
  onChange: (value: string) => void
}) {
  const { t } = useI18n()
  return (
    <Field id="currency" label={t('settings.currency')} value={value} onChange={onChange}>
      <option value="">—</option>
      {currencies.map((currency) => (
        <option key={currency} value={currency}>
          {currency}
        </option>
      ))}
    </Field>
  )
}

export function LanguageFilter({
  languages,
  value,
  onChange,
}: {
  languages: string[]
  value: string
  onChange: (value: string) => void
}) {
  const { t } = useI18n()
  return (
    <Field id="filter-language" label={t('region.filterLanguage')} value={value} onChange={onChange}>
      <option value="">{t('common.all')}</option>
      {languages.map((language) => (
        <option key={language} value={language}>
          {language}
        </option>
      ))}
    </Field>
  )
}
