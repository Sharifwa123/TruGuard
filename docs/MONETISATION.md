# Ads and going ad-free

TruGuard ships **without** an ad network: no ad SDK and no INTERNET permission. What is in place is everything around
one, so you can add your own and keep the privacy promise honest.

## What is built
- **About screen** (Home ▸ ⓘ, or Settings ▸ About): developer details, support, help, privacy, share. Edit
  `app/src/main/res/values/about.xml`; an empty value hides its row (email, website, WhatsApp, GitHub, privacy-policy
  link, payment page, price text, payment note).
- **Ad slots** (`AdSlot`, `AdsProvider`): a labelled banner on Home, Log and About only. Never on warnings, the pop-up,
  security findings, onboarding or the screen-monitor controls. With the default `NoAds` provider, slots draw nothing.
- **Ad-free supporter codes** (`SupporterLicense`, `SupporterManager`): after someone pays, you send them a code; they
  paste it in About ▸ “I have a code”. The app checks it **offline** against a public key, so there is no server and
  nobody can forge a code without your private key. Supporters never see ad slots.

## Turning on ad-free codes (one time)
```
tools/license/keygen.sh            # creates supporter_private.pem (SECRET, git-ignored) and the public key asset
git add app/src/main/assets/supporter_public_key.txt   # the public key is safe to commit
# set support_payment_url / support_price / support_payment_note in about.xml, then release
tools/license/issue.sh "Ama Mensah"   # after a payment: prints a code to send to that person
```
Back up `supporter_private.pem` somewhere safe. If you lose it you cannot issue codes that match the app; if it leaks,
anyone can make codes. Until the key exists, the “I have a code” button is hidden.

## Adding an ad network (your accounts, your decision)
1. **Pick the network and create the account** (for example Google AdMob). You need your own app ID and ad-unit IDs;
   use the network's *test* IDs until you are ready.
2. **Add its SDK** to `app/build.gradle.kts` and the manifest entries it documents. This will also add the `INTERNET`
   permission.
3. **Implement `AdsProvider`** (draw the network's banner in `Banner`) and assign it in `AppContainer`
   (`val ads: AdsProvider = YourAds(...)`).
4. **Update the privacy text**: README, onboarding, Settings and About currently promise “no internet permission,
   nothing uploaded”. With an ad SDK that is no longer true for the app as a whole, and ad networks collect device
   identifiers. Say so plainly, and add the consent prompt the network requires (Google's UMP for the EEA/UK).
5. Consider shipping **two builds**: a free build with ads and a supporter build with no network code at all.

## Things to weigh honestly
- **Trust.** TruGuard reads notifications and (optionally) screen text. People install it because it is private and
  offline. An ad SDK in the same app, even though it cannot see that data unless the app hands it over, changes how
  careful users should be and how much they will trust you. The slots are placed away from the sensitive screens for
  that reason, and ad-free supporter mode removes the network from the picture visually (a no-network supporter build,
  point 5, removes it completely).
- **Payments outside Google Play.** Play Billing only works for apps installed from Google Play. This build is
  distributed as an APK from GitHub, so payment is a link you provide (Paystack, Flutterwave, Buy Me a Coffee, a MoMo
  instruction page) followed by a code you issue by hand. If volume grows, a small server could issue codes
  automatically after a payment webhook.
- **Google Play.** Play restricts both ad-supported apps that use the Accessibility API and `QUERY_ALL_PACKAGES`.
  Putting this build on Play would need a policy review; the sideloaded APK has no such review.
