# Soneme Trend

![Soneme Trend Icon](https://github.com/userexec/soneme-trend/blob/master/soneme_trend_icon.png?raw=true)

Soneme Trend is a small, keypad-friendly Android logging, charting, and analysis app built for the Sonim XP3Plus XP3900.

It is deliberately general-purpose. Trend is not specifically a habit tracker, calorie tracker, exercise log, finance app, sleep tracker, mood journal, or anything else. A Datum can be whatever numeric thing you want to record over time, and its measurements live in an ordinary CSV file that can be copied, synchronized, inspected, or analyzed elsewhere.

The basic idea is simple: define something to measure, enter values as often as makes sense, then use **Analyses** for a deeper look at one series or **Correlations** to place several series on the same timeline.

Trend is designed around the XP3900's D-pad, numeric keypad, and three Sonim softkeys. There are no touch controls for the main interface.

![Data view screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-data.png?raw=true)  ![Datum view screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-datum.png?raw=true)  ![Analysis screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-analysis1.png?raw=true)  ![Analysis screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-analysis2.png?raw=true)  ![Analysis screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-analysis3.png?raw=true)  ![Correlations view screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-correlations.png?raw=true)  ![Correlation screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-correlation1.png?raw=true)  ![Correlation screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-correlation2.png?raw=true)  ![Correlation screenshot](https://github.com/userexec/soneme-trend/blob/master/screenshot-correlation3.png?raw=true)

## Features

* General-purpose numeric data logging
* Per-minute, per-hour, per-day, per-week, per-month, or per-year data
* Fast Increment, Set, and Decrement entry from the Datum screen
* Manual entry for measurements recorded at another date and time
* Line charts with automatic ranges and optional goal lines
* Detailed change and percent-change statistics
* Least-squares trend projection
* Estimated time to a configured goal
* Estimated future values at useful intervals
* Correlation charts comparing two to four data sets
* Mixed units and mixed Time Bases in Correlations
* Ordinary UTF-8 CSV files as the portable data store
* Safe recovery from missing files, moved storage, and application reinstalls
* External CSV editing supported by design
* Internal storage or removable SD-card storage
* Sonim softkey integration
* No accounts, analytics, advertising, subscriptions, cloud services, or runtime network access

## Tested Device

Soneme Trend has been developed and tested on:

* Sonim XP3Plus XP3900 — Android 11 Go

The interface is designed for the XP3900's 240x320 non-touch display and native three-position Sonim softkey bar. Development and testing use **Smallest width = 320 dp** in Android Developer options.

Other Android devices are not a target. A normal touchscreen phone probably will not have the Sonim softkeys the interface expects, and parts of the application may be impractical or inaccessible without them.

## Installing

Soneme Trend is distributed as a normal Android APK.

Copy the APK to the device and install it, or install it from a connected computer with ADB:

```sh
adb install soneme-trend.apk
```

If updating an existing release signed with the same release key:

```sh
adb install -r soneme-trend.apk
```

Android may require permission to install apps from unknown sources when installing directly on the phone.

## First Setup

Trend stores its portable data in a normal folder named:

```text
SonemeTrend
```

On first launch, Trend explains that no storage folder is configured and asks where the folder should live. Choose **Set up** to open Android's system folder picker.

You may select:

* an existing `SonemeTrend` folder,
* the folder containing an existing `SonemeTrend` folder,
* or a storage location where Trend should create a new `SonemeTrend` folder.

Folder-name matching is case-insensitive, so an existing capitalization variant is reused rather than duplicated.

Trend remembers access to that location. If the configured folder later disappears—for example, because an SD card was removed or replaced—Trend returns to the storage setup screen so another location can be selected.

## How Trend Is Organized

The main interface has three tabs. Use D-pad **Left/Right** to move between them and **Up/Down** to move through their lists.

### Data

Data contains the things you are recording.

Each item has a display name and is backed by one CSV file in `SonemeTrend`. Selecting an item opens its Datum screen, where you can quickly enter values, inspect recent measurements, edit its settings, add an older/future point manually, or remove individual points.

The Data list also shows how many points are available and the most recent recorded time.

### Analyses

Trend automatically provides one Analysis for every Datum.

An Analysis shows a larger inspectable chart plus calculated statistics such as change over time, percent change, trend projection, estimated time to goal, and estimated future values.

Analysis results are calculated from the current CSV whenever the view is opened. They are not stored as another data file.

### Correlations

Correlations compare **two to four** Datum on one timeline.

You choose which data sets belong to each Correlation and give the Correlation a name. Its definition is remembered by Trend, but the chart itself is rebuilt from the current CSV files whenever it is viewed.

This is useful for comparing things that may move together—or fail to move together—without forcing every measurement into one specialized tracking system.

## Creating a Datum

From **Data**, choose **New**.

A Datum has the following settings:

### Name

The friendly name shown inside Trend. Datum names must be unique, ignoring capitalization.

The display name is application metadata; it is not embedded in the CSV. This lets you rename a Datum later without renaming or rewriting the portable data file.

### CSV filename

The CSV filename used inside `SonemeTrend`.

If you enter a name without `.csv`, Trend adds the extension when checking and saving it. For example:

```text
weight
```

becomes:

```text
weight.csv
```

If that file does not exist, Trend creates it.

If the filename already exists and contains a valid, unattached Soneme Trend CSV, Trend attaches the existing file instead of replacing it. Its Units, Time Basis, and Goal are loaded from the file automatically.

### Units

What the Y-axis value means, such as:

* calories
* pounds
* millimeters
* repetitions
* dollars
* pages

The app does not interpret the unit. It is simply the label for what you are measuring.

### Time Basis

Time Basis determines the size of one logical data bucket.

Available choices are:

* per minute
* per hour
* per day
* per week
* per month
* per year

For example, a daily Datum can contain one logical value for August 20, while an hourly Datum can contain separate values for 2 PM and 3 PM on that same day.

Time Basis does **not** require you to enter data at that exact cadence. It only determines which entries belong to the same logical bucket and how Trend spaces the X-axis.

### Goal

Goal is optional.

When present, it is shown as a horizontal goal line in Datum and Analysis charts and is also used for goal-related estimates in Analysis.

After a Datum has been created, **Edit** allows its Name and Goal to change. CSV filename, Units, and Time Basis remain fixed because changing those would change the meaning or identity of the stored file.

## Quick Entry

The Datum screen is intended to make routine recording very fast.

Move focus to the **Quick entry** field and enter a number. Once the field contains a valid value, the three softkeys become:

* **Decrement**
* **Set**
* **Increment**

### Decimal and negative values on the XP3900

The XP3900's numeric keypad does not provide a normal way to type a decimal point or minus sign while a numeric field is active, so Trend provides two keypad shortcuts:

* `*` inserts a decimal point.
* `#` toggles the value between positive and negative.

These shortcuts work in **Quick entry**, the **Value** field when adding a point, and the **Goal** field in Datum Setup. They apply only while one of those numeric fields has focus; `*` and `#` keep their normal behavior elsewhere.

### Set

Sets the current Time Basis bucket to the entered value.

### Increment

Adds the entered value to the current bucket. If the bucket does not exist yet, Trend starts it at zero and then applies the increment.

### Decrement

Subtracts the entered value from the current bucket. If the bucket does not exist yet, Trend starts it at zero and then applies the decrement.

For example, if today's daily value is `12` and Quick entry contains `3`:

* Increment produces `15`
* Decrement produces `9`
* Set produces `3`

When a change is made to a bucket, the new entry time becomes that bucket's stored UTC timestamp. In ordinary use this does not change the bucket shown by Trend; it simply records when that bucket was most recently changed.

If Quick entry is empty, the Datum screen keeps the normal **Edit** and **New point** actions available. Pressing Back from an empty Quick entry field returns to Data.

Typing a numeric key while another part of the Datum screen is focused automatically moves the number into Quick entry and focuses the field.

## Adding a Point for Another Date or Time

Choose **New point** from a Datum when the measurement should not use the current time.

Trend provides its own keypad-friendly date/time editor:

* Up/Down chooses Year, Month, Day, Hour, or Minute
* Left/Right changes the selected value
* **Done** accepts the local date/time
* **Cancel** or Back discards the date/time change

Enter the measurement value and choose **Save**.

If that date/time resolves to an empty Time Basis bucket, Trend creates a new point.

If the bucket already contains a value, Trend asks what to do:

* overwrite it with the new value,
* increment it by the new value,
* decrement it by the new value,
* or cancel.

The chosen operation uses the newly entered date/time as the bucket's stored UTC timestamp.

## Removing Points and Deleting Data

Individual measurements can be removed from a Datum's point list.

Deleting a **Datum** is much more significant: Trend deletes both the Datum's application metadata **and its associated CSV file**. The confirmation dialog calls this out before deletion.

Deleting a Datum also removes it from any Correlations that use it. A Correlation that is left with fewer than two Datum is removed automatically.

Data and Correlation items can also be moved upward to control their list order. Analysis order follows Data order.

## Charts

All Trend charts are line charts.

Datum and Analysis charts fill the area beneath the line. Correlation charts omit the fill so multiple lines remain easier to distinguish.

If a Goal is configured, Datum and Analysis draw it as a horizontal goal line. Correlations intentionally do not show goal lines.

Missing Time Basis buckets remain real gaps on the X-axis. Trend connects recorded points directly rather than pretending that an unrecorded bucket had a value of zero.

If fewer than two usable points are available for a chart, Trend shows **insufficient data to chart** instead.

### Datum chart

The Datum preview shows up to the ten most recent points. It is meant as an at-a-glance view and is not directly inspectable.

### Analysis chart

Analysis charts can be inspected point-by-point with D-pad Left/Right. The selected point stays highlighted, and its timestamp and value appear in the colored readout beneath the chart.

A narrow blue glow at the left edge indicates that the chart itself currently owns focus.

Analysis automatically starts with the finest useful range currently available.

### Chart ranges

Depending on the Datum's Time Basis and available history, Trend may offer:

* All time
* Last year
* Last month
* Last week
* Last day
* Last hour

These are rolling durations, not calendar boundaries:

* year = 365 days
* month = 30 days
* week = 7 days
* day = 24 hours
* hour = 60 minutes

For example, **Last month** means the preceding 30 days, not "since the same date last calendar month."

A range is offered only when enough data exists for some part of the line to render meaningfully inside it.

## Analyses and Statistics

Analysis calculations use the complete Datum, regardless of which range is currently selected for the chart.

With enough data, Trend shows:

* overall change,
* overall percent change,
* change over useful recent periods,
* percent change over useful recent periods,
* goal projection,
* and estimated future values.

Positive changes are shown in green and negative changes in red.

Recent-window statistics stop one Time Basis above the Datum itself. For example, a daily Datum can show change over the last week, month, and year, but there is no useful "change over the last day" calculation for a series whose individual points already represent days.

If the exact beginning of a look-back window falls between two measurements, Trend linearly interpolates between the surrounding points. If no newer measurement exists, the latest measured value is carried forward to the present for that comparison.

Percent change is omitted when the comparison baseline would be zero.

### Trend projection

Trend runs a least-squares linear regression over the data.

The X-axis for regression is actual **Time Basis distance**, not merely record position. A daily series recorded on August 1, August 2, and August 5 therefore uses X positions 0, 1, and 4. Missing days remain missing days.

Monthly data is treated in monthly units: January, February, and March are 0, 1, and 2 even though the calendar months contain different numbers of days.

If a Goal exists and the regression is moving toward it, Analysis shows an estimated amount of the Datum's Time Basis remaining until the trend reaches that goal. If the measurements have already crossed the goal, it shows **Goal reached**. If the regression is moving away from the goal, Trend says that current values suggest the goal will not be reached without changes.

Analysis can also project estimated values one hour, day, week, month, or year into the future where those intervals make sense for the Datum's Time Basis.

These are simple linear extrapolations of the recorded trend, not predictions that account for seasonality, causation, or outside events.

## Correlations

A Correlation contains two to four Datum.

Create one from the **Correlations** tab, give it a name, choose the data sets, and Save.

The chart places the selected series on one common timeline. If their Time Bases differ, Trend uses the **finest Time Basis present** for the Correlation's X-axis. Coarser points naturally land at the beginning of the finer interval they represent—for example, a monthly point lands at the beginning of its month on a daily timeline.

Each line keeps its **own independent Y scale**. This is important when comparing unlike units such as pounds, calories, dollars, or repetitions. Correlation is intended to compare the timing and shape of trends; it does not imply that the numeric heights of unrelated units are directly comparable.

The currently selected line controls the visible Y-axis labels and the colored information box beneath the chart.

Use D-pad Left/Right to move through points on that line. Use **Line** to switch between the currently available series.

Correlation range choices are constrained by the coarsest Time Basis participating in the chart. Correlations default to **All time**.

If one associated CSV is temporarily missing or invalid, Trend can still open the Correlation as long as at least two associated Datum remain available. If fewer than two associated Datum files are available, selecting the Correlation opens Correlation Setup so its membership can be repaired. Separately, a line that exists but does not have enough points for the selected chart range is simply omitted; if fewer than two lines can actually be charted, Trend shows **insufficient data to chart**.

Back or Cancel from Correlation Setup does not silently change the saved membership. Changes take effect only when Save is chosen.

## CSV Files and Portability

The CSV files are the durable, portable part of Soneme Trend.

A typical file looks like this:

```csv
Timestamp,Value,Unit,Time Basis,Goal
,,calories,days,2000
2026-08-17T12:40:32Z,1450,,,
2026-08-18T16:25:01Z,1525,,,
```

The layout is intentionally simple:

* Row 1 contains the fixed headings.
* Row 2 stores Units, Time Basis, and optional Goal.
* Row 3 onward contains measurements in chronological order.
* Timestamps are complete UTC timestamps ending in `Z`.
* Values and Goal are ordinary decimal numbers.

Valid Time Basis values in the CSV are:

```text
minutes
hours
days
weeks
months
years
```

Trend accepts normal UTF-8 CSV quoting and both LF and CRLF line endings.

The CSV can be copied to another computer, synchronized with something such as [Soneme Sync](https://github.com/userexec/soneme-sync), edited with ordinary tools, or used by your own scripts and analysis software.

### External edits

Trend assumes CSV files may change outside the app.

Views re-read their files rather than treating an old in-memory copy as authoritative, and every operation that changes a CSV re-reads and validates the current file immediately before writing.

If a file has changed but is still valid, Trend operates on the newer contents. If it has become missing or invalid, Trend refuses to write over it and returns to a safe recovery path instead.

This makes external editing practical, but it is still possible to surprise yourself by editing the same value on another machine while simultaneously entering a change on the phone. Trend protects the file structure; it cannot infer which human edit you intended to win.

## What Trend Stores Where

Trend deliberately splits portable measurement data from application-only organization.

### In the `SonemeTrend` folder

Each Datum CSV stores:

* Units
* Time Basis
* Goal
* all recorded measurements

### In Trend's private application storage

Trend keeps:

* Datum display names
* the association between each Datum and its CSV filename
* Data ordering
* Correlation names and membership

This is why the CSV files remain useful even without the app, and also why reinstall recovery works the way it does.

## Reinstall and File Recovery

Uninstalling Trend removes its private application metadata, but CSV files in the user-selected `SonemeTrend` folder remain on the filesystem.

After reinstalling:

1. Set up Trend with the same storage location.
2. Open Data and choose **New**.
3. Give the Datum whatever display name you want.
4. Enter the filename of an existing valid CSV.

Trend recognizes the existing file, loads its Units, Time Basis, and Goal, and attaches it without rewriting the recorded measurements.

You can repeat this for each surviving CSV.

Because display names, ordering, and Correlation definitions live in private application storage, those pieces must be recreated after an uninstall or application-data reset. The actual measurement history remains in the CSV files.

If an already configured Datum later reports that its CSV is missing or unreadable, selecting that Datum opens a recovery form. Choose a valid unattached replacement CSV to reconnect it.

## Time Zones and Local Dates

CSV timestamps are stored as UTC for portability, but UTC is never shown in Trend's normal interface.

Whenever data is read, Trend converts each UTC timestamp into the phone's **current local time** and then reduces it to the Datum's Time Basis.

For example, a daily point is displayed as a local day, while a monthly point is displayed as a local month. Week boundaries follow the phone's current locale.

This means travel can legitimately change which local bucket an old UTC timestamp falls into. A point remembered as one local day in one time zone may appear as the previous or next day after moving far enough east or west. Trend treats this as a consequence of interpreting the same portable UTC instant in the phone's current local context, not as file corruption.

In the rare case where two stored UTC rows resolve to the same local Time Basis bucket, Trend displays the newest one as that bucket's value. Removing that displayed bucket removes all raw rows currently resolving to it.

## Navigation and Softkeys

Trend is designed around hardware navigation.

At the top level:

* D-pad Left/Right changes Data, Analyses, and Correlations tabs.
* D-pad Up/Down moves through lists.
* D-pad Center opens the focused item.

Inside forms, Up/Down moves among controls and the Sonim softkeys provide context-appropriate actions such as Save or Cancel.

Inside inspectable charts, Left/Right moves through data points. Correlations also provide the **Line** softkey for choosing which line is active.

Back generally returns to the previous logical screen. Unsaved setup changes are discarded when leaving without Save.

## Storage and Privacy

Soneme Trend is intentionally local-only.

It does not require:

* an account,
* internet access while running,
* Google Play Services,
* analytics,
* advertising,
* a subscription,
* or a vendor cloud service.

The application does not request Android's Internet permission.

Measurement CSV files are stored only in the `SonemeTrend` location you select. Trend's small registry of display names, ordering, and Correlation definitions remains in private application storage, and Android application backup is disabled.

If you choose to synchronize the CSV files elsewhere, privacy and security then depend on the transfer method and destination you choose.

## Building

Soneme Trend is a standard Gradle Android project.

The build requires JDK 17 and Android SDK platform 34.

Build a debug APK with:

```sh
./gradlew assembleDebug
```

For a configured signed release build:

```sh
export SONEME_KEYSTORE=/path/to/keystore.jks
export SONEME_STORE_PASSWORD='...'
export SONEME_KEY_PASSWORD='...'
./gradlew assembleRelease
```

The configured release key alias is `soneme`.

The resulting APK is written beneath:

```text
app/build/outputs/apk/
```

The application targets Android 14 APIs while supporting Android 11 and newer (`minSdk 30`). Future APK updates installed over an existing release must use the same signing identity.
