# Firebase Remote Config: onboarding native placements

The onboarding native ads use the existing Remote Config parameters and native-ad loader. Merge
the following objects into the JSON already published under `stm_ads_config` (or the compatible
legacy parameter `ads_config`):

```json
{
  "onboardingGetStartedNative": {
    "enabled": true,
    "maxPerSession": 10
  },
  "defaultSmsNative": {
    "enabled": true,
    "maxPerSession": 10
  }
}
```

Each `enabled` value is independent. Setting one to `false` prevents that placement from making an
ad request or displaying an ad. Both placements also require the existing `stm_ads_enabled` master
parameter to be `true`.

The existing ad-type JSON under `stm_ad_type_config` (or `ad_type_config`) should include:

```json
{
  "onboardingGetStarted": "native",
  "defaultSms": "native"
}
```

Use `"none"` instead of `"native"` only when the placement should also be disabled through the
central ad-type configuration. Missing ad-type fields retain the native behavior for compatibility.

No new AdMob unit IDs are required. Both placements use the centralized native primary and backup
IDs in `AdUnitIds`; when the existing `stm_ad_test_mode` setting is enabled, both resolve to Google's
test Native Ad unit ID.
