# Spamless

**پیامک‌های مرتب‌تر، تبلیغات کم‌صداتر، و رمزگذاری اختیاری متن پیام‌ها.**

**A cleaner SMS inbox, quieter spam alerts, and optional message encryption.**
<p align="center">
  <a href="https://github.com/MoRanjbarDi/Spamless/releases/download/release/Spamless-1.2.0-release.apk"
     style="display:inline-block;background-color:#2ea44f;color:white;padding:18px 45px;border-radius:10px;text-decoration:none;font-size:20px;font-weight:bold;">
    ⬇ DOWNLOAD SPAMLESS 1.2.0
  </a>
</p>

**Disclaimer:** Spamless is a personal hobby project, and there are currently no plans to publish it on Google Play or other app stores. GitHub-only distribution is a choice, not a security warning. The source code is open for anyone to inspect, and spam filtering happens on your device. To make sure you’re getting an official build, download Spamless directly from the project’s GitHub Releases page.

**یادداشت توسعه‌دهنده:** Spamless یک پروژه شخصی از فرط کلافکی از تعداد پیامک های تبلیغاتی بی در و پیکر است. هنوز اینقدر جدی نشده که بدانم قصد انتشار در بازارگاه های اپ مانند گوگل پلی را دارم یا نه یا در آینده چه اتفاقی می خواهد بیفتد؟ اما تمام مراحل ضد هرزنامه داخل گوشی شما اتفاق می افتد، هیچ مخزن آنلاین مشترکی برای کراود سورس سرشماره های تبلیغاتی یا اتفاقاتی از این دست که  Spamless را به یک برنامه متصل و وابسته به اینترنت تبدیل کند وجود ندارد. 
آخرین ورژن را همیشه از گیتهاب خودم دانلود کنید.


[فارسی](#fa) · [English](#en) · [دانلود / Releases](https://github.com/MoRanjbarDi/Spamless/releases)

<a id="fa"></a>
<div dir="rtl">

## فارسی

Spamless یک برنامهٔ متن‌باز اندرویدی برای مدیریت SMS و MMS است. Spamless **متن پیام را برای تشخیص هرزنامه بررسی می‌کند**، گفت‌وگوهای مخاطبان و شماره‌های ذخیره‌نشده را در زبانه‌های جدا نشان می‌دهد و به شما امکان می‌دهد **متن پیام‌ها را با یک کلید مشترک رمزگذاری کنید**.

### شروع استفاده

Spamless را نصب کنید و به‌عنوان **برنامهٔ پیش‌فرض پیامک** انتخاب کنید. برای تفکیک گفت‌وگوهای مخاطبان از بقیه، دسترسی به مخاطبان را بدهید. برای نمایش اعلان‌ها نیز دسترسی اعلان را فعال کنید.

### فیلتر پیامک‌های تبلیغاتی

Spamless به **متن هر پیام** نگاه می‌کند، نه فقط به سرشمارهٔ آن. به همین دلیل، ممکن است پیام خدماتی یک فرستنده در صندوق ورودی بماند و پیام تبلیغاتی همان فرستنده به پوشهٔ «هرزنامه» برود. بررسی متن روی خود گوشی انجام می‌شود.

برای تغییر این گزینه‌ها، از منوی کناری به **تنظیمات ← تنظیمات هرزنامه** بروید.

1. **فیلتر پیامک‌های تبلیغاتی ایران** را روشن کنید تا الگوهای مختص پیامک های تبلیغاتی ایرانی را شناسایی کرده و آن ها را به پوشه هرزنامه بفرستد. با این حال، برخی از پیامک های تبلیغاتی (بخصوص پیامک های تبلیغاتی خود اپراتورها که تعدادشان هم زیاد نیست)این الگو را ندارند و گاهی ممکن است پیامی تبلیغاتی همچنان در صندوق اصلی مشاهده شوند. (در این موارد می توانید سرشماره را به صورت دستی بلاک کنید)
2. در **عبارت منظم دلخواه** می‌توانید با قالب Regex جاوا، الگوی خودتان را اضافه کنید. کافی است متن پیام با یکی از قوانین فعال مطابقت داشته باشد تا به هرزنامه برود. برای غیرفعال کردن قانون دلخواه، این کادر را خالی بگذارید.
3. در **اعلان‌های هرزنامه** می‌توانید اعلان روی صفحه را خاموش کنید، تنظیم کنید پس از ۱۰ ثانیه ناپدید شود یا آن را روشن نگه دارید.
4. در **صدای اعلان هرزنامه** یکی از «بی‌صدا»، «بوق کوتاه» یا «صدای پیش‌فرض اعلان پیامک» را انتخاب کنید. صدا مستقل از اعلان روی صفحه است، یعنی حتی اگر نمایش اعلان را خاموش کنید، بوق همچنان می‌تواند پخش شود.
5. **لرزش هرزنامه** را با گزینهٔ **Vibrate for spam messages** جدا از صدا تنظیم کنید. لرزش به‌طور پیش‌فرض روشن است و از حالت بی‌صدا و «مزاحم نشوید» گوشی پیروی می‌کند.
6. در **حذف خودکار پیام‌های هرزنامه** مدت نگهداری پیام‌ها را تعیین کنید یا «هرگز» را انتخاب کنید. اگر سطل زباله فعال باشد، پیام‌هایی که خودکار حذف می‌شوند ابتدا به آنجا می‌روند.

> **پیشنهاد می‌کنیم دست‌کم یکی از سه گزینهٔ نمایش اعلان، لرزش یا صدای هرزنامه را فعال نگه دارید.** تعداد بسیار اندکی از سرویس‌ها، همچون اپلیکیشن پرداخت تارا، گاهی کد تأیید (OTP) را از سرشمارهٔ تبلیغاتی می‌فرستند. اگر پیامک‌های آن سرشماره ملزم به درج عبارت‌های مختص سرشماره تبلیغاتی باشند، ممکن است Spamless کد تأیید را هم به‌خاطر همین عبارت به پوشهٔ هرزنامه بفرستد. در این حالت، اعلان، لرزش یا صدا خبرتان می‌کند که آن پوشه را بررسی کنید.

#### اگر پیامی اشتباهی به هرزنامه رفت

- برای خارج کردن **فقط یک پیام** از هرزنامه، گفت‌وگو را در پوشهٔ هرزنامه باز کنید، انگشتتان را روی همان پیام نگه دارید و **Mark as not spam** را بزنید. پیام‌های بعدی فرستنده همچنان بررسی می‌شوند.
- برای **مجاز کردن یک فرستنده**، در پوشهٔ هرزنامه انگشتتان را روی گفت‌وگو نگه دارید و از منوی سه‌نقطه **Always allow this sender** را بزنید. پیام‌های فعلی او از هرزنامه خارج می‌شوند و پیام‌های بعدی‌اش دیگر فیلتر نمی‌شوند.
- اگر فقط می‌خواهید **پیام‌های عادی و هرزنامه را کنار هم ببینید**، در منوی گفت‌وگو در صندوق ورودی **Show spam messages** را روشن کنید. این کار دسته‌بندی پیام‌ها را تغییر نمی‌دهد.

### زبانه‌های مخاطبان و غیرمخاطبان

زیر نوار جست‌وجو سه زبانه قرار دارد.

- **همه (All)** تمام گفت‌وگوهای عادی صندوق ورودی را نشان می‌دهد. هرزنامه‌ها در پوشهٔ جداگانه می‌مانند.
- **مخاطبان (Contacts)** گفت‌وگوهایی را نشان می‌دهد که دست‌کم یکی از طرف‌های گفت‌وگو در مخاطبان گوشی ذخیره شده است.
- **غیرمخاطبان (Others)** گفت‌وگوهایی را نشان می‌دهد که هیچ‌یک از طرف‌های گفت‌وگو در مخاطبان گوشی ذخیره نشده‌اند.

از **تنظیمات ← عمومی ← زبانهٔ پیش‌فرض صندوق ورودی** انتخاب کنید که برنامه با کدام زبانه باز شود. بعد از ذخیره یا حذف یک مخاطب، فهرست صندوق ورودی را به پایین بکشید تا به‌روز شود. جست‌وجو هم فقط در زبانه یا پوشه‌ای انجام می‌شود که باز کرده‌اید.

### رمزگذاری متن پیام‌ها

رمزگذاری اختیاری است. برای فرستادن و خواندن پیام رمزگذاری‌شده، **هر دو نفر باید Spamless نسخهٔ ۱.۲.۰ یا جدیدتر و یک کلید مشترک داشته باشند**.

1. گفت‌وگو را باز کنید و در بخش اطلاعات گفت‌وگو، گزینهٔ **Encryption Key** را بزنید.
2. یک کلید بسازید یا کلید مشترک را وارد کنید. برای رساندن کلید به طرف مقابل از روشی مطمئن استفاده کنید، مثلاً حضوری کد QR را اسکن کنید.
3. با نماد قفل در نوار بالای گفت‌وگو، رمزگذاری را روشن یا خاموش کنید.
4. اگر می‌خواهید برای گفت‌وگوهای بدون کلید اختصاصی یک کلید پیش‌فرض داشته باشید، به **تنظیمات ← تنظیمات رمزگذاری ← Global Encryption Key** بروید. طرف مقابل همچنان باید همان کلید را داشته باشد.

با روشن بودن رمزگذاری، متن پیام‌های جدید با **AES-256-GCM** رمزگذاری می‌شود. تصاویر و دیگر پیوست‌های MMS مشمول این رمزگذاری نیستند. کلیدها را در جای امنی نگه دارید. اگر برنامه را حذف کنید یا داده‌های آن را پاک کنید، ممکن است بدون کلید دیگر نتوانید پیام‌های رمزگذاری‌شده را بخوانید.

### امکانات دیگر

- **ارسال پیامک‌های طولانی.** Spamless متن‌های بلند را به‌صورت پیامک چندبخشی (SMS) می‌فرستد، بدون اینکه آن‌ها را خودکار به MMS تبدیل کند. این تبدیل در بعضی گوشی‌ها هنگام استفاده از Google Messages یا Samsung Messages رخ می‌دهد.
- **جست‌وجو در متن پیام‌ها.** نتیجهٔ جست‌وجو بخشی از متن هر پیام را نشان می‌دهد و با انتخاب آن مستقیماً به همان پیام می‌روید. پوشهٔ هرزنامه هم جست‌وجو و گزینهٔ «انتخاب همه» دارد.
- **سطل زباله.** به‌طور پیش‌فرض، پیام‌های حذف‌شده تا ۵ روز قابل بازیابی‌اند. از بخش **تنظیمات ← عمومی** می‌توانید سطل زباله را فعال یا غیرفعال کنید و مدت نگهداری پیام‌ها را تغییر دهید.

</div>

---

<a id="en"></a>

## English

Spamless is an open-source Android SMS/MMS app built around three features: **text-based spam filtering, separate contact and non-contact inbox views, and optional message encryption**.

### What makes Spamless different?

- **Filtering by message content:** Matching messages move to Spam. Ordinary and promotional messages from the same sender can appear in separate folders.
- **A built-in rule for Iranian promotional SMS:** Recognizes cancellation text using `11`, Persian `۱۱`, and Arabic-Indic `١١`. Rule matching runs on your device.
- **Custom rules:** Add your own Java regular expression to match other unwanted message patterns.
- **Three tabs below search:** All, Contacts, and Others help separate personal conversations from service messages and unsaved senders.
- **Optional encryption:** Two people with a shared key can exchange encrypted message text.

### Getting started

Install Spamless, select it as your **default SMS app**, and grant the required permissions. Contacts access enables contact/non-contact separation; notification permission enables visible alerts.

### Configure spam filtering

Open the navigation drawer, then **Settings → Spam settings**:

1. **Filter Iranian ads:** Enable or disable the built-in rule. It recognizes text that only go in iran's promotional messages. It is a pattern-based rule: some ads will not match, and some legitimate messages may match.
2. **Custom regular expression:** Enter a Java Regex for additional patterns. Leave it blank to disable the custom rule. A match against either enabled rule sends the message to Spam.
3. **Spam notifications:** Choose Off, Dismiss after 10 seconds, or On.
4. **Spam notification sound:** Choose Silent, Beep, or Default SMS notification sound. Sound is independent of visible notifications, so Beep can still play when visual spam notifications are Off.
5. **Vibrate for spam messages:** Set vibration independently of sound. It is enabled by default and respects silent mode and Do Not Disturb.
6. **Delete spam messages automatically:** Choose a retention period or Never. When Trash is enabled, automatically deleted messages go there first.

> **Recommended: set the spam sound to Beep.** Some services send one-time passwords (OTPs) from sender IDs also used for promotional SMS. If a rule mistakenly routes an important message to Spam, a short beep lets you know to check that folder. Sharing a sender ID alone does not classify a message as spam—the filter checks its text. Phone sound settings and Do Not Disturb still apply.

**Correct a classification:**

- **Allow a sender:** In Spam, long-press a conversation, open the three-dot menu, and choose **Always allow this sender**. Current messages move out of Spam, and future messages from that sender are exempt from filtering.
- **Allow only a message:** Inside a spam conversation, long-press the message and choose **Mark as not spam**. This does not allow every future message from its sender.
- **View both parts of a conversation:** Enable **Show spam messages** from the inbox conversation’s menu. This changes visibility without changing spam classifications.

### Separate contacts and non-contacts

The tabs below search are:

- **All:** Regular inbox conversations from both saved and unsaved senders. Spam remains in its own folder.
- **Contacts:** Conversations with at least one recipient saved in your phone’s contacts.
- **Others:** Conversations with no saved recipients.

Choose the opening view in **Settings → General → Default inbox tab**. After saving or deleting a contact, pull down on the inbox to refresh. Search follows the selected tab or folder.

### Configure encryption

Encryption is optional. **Both participants need Spamless 1.2.0 or newer and the same shared key** to exchange newly encrypted messages.

1. Open a conversation, go to its conversation information/settings, and select **Encryption Key**.
2. Generate a key or enter a shared key. Exchange it through a trusted channel, such as scanning the QR code in person.
3. Use the lock control in the conversation toolbar to enable or disable encryption.
4. To configure a default key, open **Settings → Encryption settings → Global Encryption Key**. Conversations without their own key use this default; each recipient still needs the matching key.

New message text uses **AES-256-GCM** encryption. This feature does not encrypt MMS images or attachments. Keep shared keys somewhere secure: uninstalling the app or clearing its data can remove access to encrypted messages.

### More features

- **Message-level search:** Results show individual messages with relevant excerpts. Opening a result jumps to that message. Spam also supports search and Select all.
- **Trash:** Deleted messages are recoverable for 5 days by default. Configure Trash and its retention period under General settings.
- **Independent spam alerts:** Choose how much attention spam receives without tying its sound to visible notifications.

---

## Build from source / ساخت از کد منبع

Requires **JDK 11**, **Android SDK Platform 36**, and **Build-Tools 29.0.3 and 36.0.0**. Use the included Gradle wrapper.

برای ساخت، پیش‌نیازهای بالا را نصب و مسیر SDK را در `local.properties` یا متغیر `ANDROID_HOME` تنظیم کنید. / Configure your SDK path in `local.properties` or `ANDROID_HOME`, then run:

```sh
chmod +x gradlew
export REALM_DISABLE_ANALYTICS=true
./gradlew :presentation:assembleNoAnalyticsRelease
```

برای ساخت نسخهٔ امضاشده، متغیرهای `SPAMLESS_KEYSTORE`، `SPAMLESS_KEY_ALIAS` و `SPAMLESS_KEYSTORE_PASSWORD` را تنظیم کنید. برای به‌روزرسانی نصب‌های قبلی، همان کلید امضا را نگه دارید. / Set `SPAMLESS_KEYSTORE`, `SPAMLESS_KEY_ALIAS`, and `SPAMLESS_KEYSTORE_PASSWORD` for a signed release. Keep the same signing key for updates.

Application ID: `com.moranjbardi.spamless`

Current version: **1.2.2**

## Credits & license / سازندگان و مجوز

Created by **Moranjbardi**, based on [Partisan-SMS](https://github.com/wrwrabbit/Partisan-SMS) and [QKSMS](https://github.com/moezbhatti/qksms). Credits: **Moranjbardi, Cpartisans, Moez Bhatti**.

- [Source / کد منبع](https://github.com/MoRanjbarDi/Spamless)
- [Releases / دریافت نسخه‌ها](https://github.com/MoRanjbarDi/Spamless/releases)
- [Website / وب‌سایت](https://moranjbardi.github.io/)
- **GNU General Public License v3.0** — see [LICENSE](LICENSE).
- © 2014–2026. Original copyright and license notices are preserved.
