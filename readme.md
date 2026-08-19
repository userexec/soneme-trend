# Soneme Trend

Soneme Trend is a minimal logging and charting app that lets you define trends you'd like to track, then quickly enter data on a routine basis to build out a chart. Multiple trends can be viewed together on a chart to pick out correlations.

The key to Soneme Trend is it's general purpose, portable, and non-prescriptive. It's not a tracker exclusively for your habits, mood, money, calories, sleep, period, bench press, or anything else. It's whatever you want of it, sitting there as a clean CSV transferrable with Soneme Sync if you have deeper analysis needs. It's the core of every tracker app, minus the chrome, the account, and the subscription.

# Target device properties

The Sonim XP3900 has the following constraints:

- 240x320
- Android 11 Go
- No touchscreen
- Options menu softkeys
- No Google Play Store or services
- App must be sideloaded as an .apk
- Smallest width set to 320 dp in developer options

## Application overview

On first setup, application asks the user to select a storage folder. First, an informational screen is shown explaining that no SonemeTrend folder is configured and that the user should select either the location containing an existing one or where a new one should be created. Softkeys are Exit (blank) Set up. Set up launches DocumentsUI to pick a folder, Exit returns to launcher. Cancelling from DocumentsUI without picking a folder puts the user back on the informational screen.

Resolve the actual SonemeTrend folder as follows:

 - If the selected folder itself is named "SonemeTrend" (case-insensitive), use it.
 - Otherwise, if the selected folder already contains a child folder named "SonemeTrend" (case-insensitive), use that existing child.
 - Otherwise, create a new "SonemeTrend" child folder inside the selected folder and use it. Persist access to the selected storage tree and remember the resolved SonemeTrend folder within it. Do not create a second SonemeTrend folder merely because an existing one uses different capitalization, and do not rename an existing case variant just to normalize capitalization.

Application has three tabs: Data, Analyses, and Correlations

Application normally opens to Data view.

The data tab is a listing of trends you're tracking. These are referred to in this spec as Datum, and are interacted with in the Datum view.

The analyses tab is a listing of analyses, each of which are more in-depth views of the each trend. Analyses are automatically defined, one per Datum, and while their listing is coupled to the data listing, the information shown in them is assembled only when requested--the statistics etc. that are shown are not saved to CSV, for example.

The correlations tab is a listing of custom reports, which are set up by choosing between two and four datasets to see together on the same chart. Correlations are user-defined, and while the definition is persistent, each viewing of the resulting chart is assembled only when requested.

Both analyses and correlations are generated when requested using the data from the trends. The trends themselves are the only things that leave artifacts in the form of CSVs on the filesystem.

Data is saved in CSV files under the SonemeTrend folder selected. Each CSV file contains "Timestamp", "Value", "Unit", "Time Basis", and "Goal" in row 1 columns 1-5, valid timestamps in column 1 rows 3-x, values in column 2 rows 3-x, units string in column 3 row 2, time basis string in column 4 row 2, and empty cell or number or float in column 5 row 2. In summary, headings are on row 1, settings are on row 2, and data starts on row 3.

```
Timestamp,Value,Unit,Time Basis,Goal
,,calories,days,2000
August 17 2026,1875,,,
August 18 2026,1940,,,
```

The concept of time basis bears some explaining. Time basis determines how data points are divided into rows. Valid options are "minutes", "hours", "days", "weeks", "months" and "years". If the time basis is minutes, then each data point is for one minute, and two rows in the CSV may not share timestamps in the same minute. When the timestamp is represented, it is represented out to the minute. If the time basis is in months, then two rows in the CSV cannot share the same month. When recording data, the current data/time is reduced to a timestamp appropriate to the dataset's time basis. 

Timestamp formats:
 - August 17, 2026, 2:43 PM
 - August 17, 2026, 2 PM
 - August 17, 2026
 - August 16-22, 2026
 - August 2026
 - 2026

Any time a timestamp needs to be converted and it is missing information to do so, e.g. a year timestamp needs to be converted to a minute timestamp, the first possible value is assumed for any missing. For example, the year timestamp 2026 converted to a minute timestamp is January 1, 2026, 12:00 AM. August 16-22, 2026 converted to hour is August 16, 2026, 12 AM. Conversion is only used in Correlation views where datasets in multiple timebases may need to be displayed on the same chart.

Whether week dates are Sunday-Saturday or Monday-Sunday should be based on the phone's locale.

Charts may be implemented as a custom Android View; an external charting library is not required.

## Charts

All charts are line charts.

Datum and Analysis views have a fill under their line of their color at 40% opacity. Correlation view omits the fill to make lines more visible in comparison to each other.

Goal line, if a goal value is present for dataset, is a horizontal line across the chart.

Points when focused enlarge by 2x size and take on the highlight color.

 - Line color is #4F6F8F
 - Highlight color is #0070E0
 - Goal line color is #00FF00

Line colors when multiple lines are present are #4F6F8F, #B07156, #7B9E72, and #AB4E68

Highlight colors are respectively #0070E0, #FF5005, #3FFF0F, and #FA0047

Line charts respect empty X axis time units and draw straight lines between data points that may be separated by unusued X axis. The data does not fall to 0 if a unit of time does not have a row with a value for it. In Correlation view, some lines may not reach the edges. Where the data cuts off, the line simply stops.

If the chart is inspectable (Analysis and Correlation views) the user can cycle through data points shown using left and right on the D-pad. The data point is highlighted on the chart and its timestamp and value are shown in a color-matched box below the chart.

Chart header, to left:

"[data points in view] records in range"

Chart header, to right:

Time range select menu, "All time", "last year", "last month", "last week", "last day", with options logically eliminated by the current dataset's time basis (e.g. monthly data will not have a "last month", "last week", or a "last day" option since that would mean one or fewer data point could be rendered). Note "last year" means 365 days, "last month" means 30 days, "last week" means 7 days, and "last day" means the previous 24 hours/1440 minutes. The chart's timeframe just means "look back by the common amount of time meant by this word," not literally "previous calendar month."

The chart header is not used on the Datum view.

If a chart header/range selector is called for by the view (Analysis and Correlation views), the X-axis range determined by time range select (shrinks to available data if not enough points to fill X axis with data). One additional data point if available is charted off the left of the chart so that the line comes from somewhere.

If a chart header/range selector is not called for (Datum view), the last 10 data points are charted and they form the range bounds. If fewer than 10 are available, then as many as are availble.

If any chart has fewer than two data points available in its range (or fewer than two in total if charting without a range selector), the chart is omitted from the view. A placeholder box with "insufficient data to chart" is shown.

Y axis range determined by data values being shown.

Y axis top value is the highest charted point's value (or possibly goal line value in Datum and Analysis view, see later rules) with 2 added to its second most significant digit and remaining digits zeroed (e.g. 215 becomes 230, 3451 becomes 3600, 0.0757 becomes 0.077). If the top value is negative, then 2 is removed from its most signifiant digit instead and remaining digits are zeroed (e.g. -215 becomes -190, -3451 becomes -3200, -0.757 becomes -0.73).

Y-axis bottom value works the same way but in reverse--positive numbers get 2 removed from second most significant digit, negative numbers get 2 added. Same deal if goal line is below the lowest point, it forms the lowest bound.

In Datum and Analysis views, goal lines are drawn and if higher than the charted data's highest point value, or lower than the charted data's lowest point value, they are considered the highest or lowest value respectively instead of the highest or lowest point for the purposes of Y axis range. Goal lines are not used in Correlation views.

Y axis labels and tick marks are at minimum, maximum, and two intermediary values.

X axis labels are never shown on the the chart itself. The X range is presented in the views' headings centered text consisting of the date/time of the chart's left edge (whether or not a data point is on that value), and the date/time of the last data point shown. In Datum views, since a data point will always be the left edge, the date/time of the first and last data points charted are used.

X axis tick marks are based on the range selected e.g. last week shows ticks for days, last year shows ticks for months. "All time" omits tick marks. Datum view also omits ticks on the X axis.

## Views

### Data

#### Controls

- Back returns to launcher

#### Main content

List of Data items. Clicking one opens its Datum view.

Items are the name (marquee if too long), subtext of "X data points" to the left, date/time of last entry (pretty) on the right.

#### Options menu

 - Delete

   Opens confirmation "Delete [name]? This will also remove all data points recorded.", options "Cancel" (default) and "Delete". Choosing delete deletes the Datum and its settings, and also its associated CSV.

   On delete also remove this Datum from the configuration of any Correlations. If a Correlation fell below 2 Datum, automatically delete the Correlation.

 - Move up

   Not available for first item in list. Moving items in Data list also moves them in Analyses list.

 - New

   Opens Datum Setup view


### Analyses

#### Controls

- Back switches to Data tab

#### Main content

List of available analyses, one per Datum item with same name, in the order of the Data menu. Items are only the name, marquee if too long. Clicking an item opens its Analysis view.

#### Options menu

 - (blank)

 - (blank)

 - (blank)


### Correlations

#### Controls

- Back returns to Analyses tab

#### Main content

List of available Correlation items. Name first (marquee if too long), then subtext one-per-line of Analysis items this correlation is made from.

#### Options menu

 - Delete

   Opens confirmation "Delete [name]?"

 - Move up

   Not available for first item in list.

 - New

   Opens Correlation Setup view



### Datum Setup

#### Controls

- Back returns to Data view without saving

#### Main content

Form:

Name - Name to be displayed in the Data list. Must be unique among Data items, case-insensitive.

CSV filename - file is saved to the SonemeTrend folder. User is allowed to choose a filename since it's assumed this file will be transferred for use elsewhere with Soneme Sync and may have special naming considerations. Name must contain only filename-safe characters, end in .csv, and must be unique in the SonemeTrend folder.

Units - What are you measuring? Show label "Units, Y-axis", line below label "e.g. calories, repetitions, millimeters"

Time basis - By what unit of time are measurements considered a single data point? Show label "Time basis per measurement, X-axis", lines below label "Determines when a data point is incremented versus when a new data point is created." "How often do you plan to take measurements?". Input is a select with options for "per minute", "per hour", "per day", "per week", "per month", and "per year".

Goal - Optional value, draws a horizontal line on the chart and is used in an Analysis display. Must be a number or float.


Text block below form:

Examples:

Calories per day

Millimeters per hour

Repetitions per week

Books per month


If a CSV filename that already exists is entered, if it is not associated with any other data items and it contains a valid Soneme Trend layout and values, set the units, time basis, and goal fields to match this CSV and disable entry into them unless filename is changed again to one that doesn't exist. This is how recovery and adding pre-made files works.

This view may be opened again with the Edit option under Datum view. If editing an existing Datum, lock the CSV filename, Units, and Time basis fields. Only the Name and Goal may be adjusted.

#### Options menu

 - Cancel

 - (blank)

 - Save



### Datum

#### Controls

- Back button returns to Data view unless cursor is in quick entry field, in which case it will naturally act as a backspace. User will need to change focus to activate back. An invisible anchor at top of page may need to be provided so that D-pad up can be used to unfocus the quick entry field naturally, but this may also be handled with normal scroll actions, really not sure--might just require testing.

#### Main content

Header, to left:

Datum name, marquee if too long

Subtext, "per [time base]"

Header, to right:

Total data points collected,
Subtext "Records"

Small chart preview of up to 10 most recent points, roughly half screen height. No chart header/range selector.

Quick entry field - Adds a data point with a timestamp of now. Only accepts valid whole numbers and floats. Keying input method always changes to 123 (numeric) when this field is focused--not sure how that works, but some form of marking the field as numeric only likely activates this on the system.

Data points heading

Data points list with focusable data points presented as two columns, Timestamp and Value. List is in descending order of points' timestamps, most recent first. Focus focuses both columns for a given point and shows a Remove option in the options menu. Removal of a point refreshes the chart preview, but focus stays where it was. It is now on the following data point in the list if additional points existed, or on the previous data in the list if removed was the last in list. If no data points remain, focus goes to the quick entry field.

"No data recorded yet" displayed if no entries.

If at any point in this view the user types a number on the keypad while not focused into the quick entry field, insert the number into the quick entry field and focus the quick entry field with the cursor at the end.

#### Options menu

When the quick entry field is not focused, options are:

 - Remove

   Only appears if focus is on a data point item. Opens confirmation 'Remove data point "[value]" from [timestamp]?'. Options "Cancel" (default) and "Remove". Remove removes the row in the CSV and refreshes this Datum. Focus behavior is described above in Main Content heading for Datum view.

 - Edit

   Opens Datum Setup

 - New Point

   Opens Datum Add

When quick entry field is focused, options are:

 - Decrement

   Decrements the current timestamp's value with the number in the quick entry field. If no data point exists for the current timestamp, create one and assume its initial value would have been 0. Option disappears when value of quick entry field is not a valid number or float.

 - Set

   Sets this timestamp's value to the number in the quick entry field. If no data point exists for the current timestamp, create one. Option disappears when value of quick entry field is not a valid number or float.

 - Increment

   Increments the current timestamp's value with the number in the quick entry field. If no data point exists for the current timestamp, create one and assume its initial value would have been 0. Option disappears when value of quick entry field is not a valid number or float.


### Datum Add

#### Controls

 - Back returns to Datum view

#### Main content

Manually enter data points using this view. Useful for when adding multiple measurements recorded elsewhere over time, or situations where you need to add a data point outside of the current timestamp.

If Save is clicked and the timestamp entered (once resolved to its time base representation) already exists in the dataset, blank the options menu and pop up a menu with explanation "[timebase resolved timestamp] already has data." and options "Overwrite" "Increment" "Decrement" and "Cancel". Clicking an option performs the requested action on that data point's value, cancel returns to the Datum Add form and restores the options menu as it was before clicking Save.

Fields:

Date/time picker

Value

#### Options menu

 - Cancel

   Returns to Datum view without saving

 - (blank)

 - Save

   Saves new data point


### Analysis

#### Controls

- Back returns to Analyses view
- Left and right D-pad move between available data points on chart
- Up and down D-pad scroll

#### Main content

Header, to left:

Analysis name, marquee if too long

Subtext, "per [time base]"

Header, to right:

Total data points collected,
Subtext "Records"

Chart with header and range selector, large format, taking up most of screen.

Colored bar in chart's line color with white text takes up remaining "above the fold" screen. Focus in this view begins on the right-most data point in the chart, and only the chart points are focusable in this view. Colored bar shows data point timestamp to left and value to right.

Statistics heading

If less than 2 data points exist, "insufficient data for statistics" is shown.

Change over time subheading

Overall change - Large number (green with green up arrow if increase, red with red down arrow if decrease):

Smaller numbers (normal formatting, black text):
Change over [largest range with complete data, e.g. "last year"], repeat with each smaller range until dataset time base is reached.

Percent change subheading

All time - Large number (green with green up arrow if increase, red with red down arrow if decrease)

Smaller numbers (normal formatting, black text):
% change over [largest range with complete data, e.g. "last year"], repeat with each smaller range until dataset time base is reached.

Changes ideally assume data exists on the points in question, i.e. for "last year" hopefully there's a data point today and exactly 365 days ago. If not, then assume today's value would be unchanged from the most recent value, and if no point exists 365 days ago, interpolate between the two data points on either side of the 365 day mark. If a data point only exists 364 days ago, there is not enough data and "% change over last year" would need to wait one more day to be shown. This pattern repeats down to the dataset time base. Smaller ranges are thus more likely to have "% change over" entries, but the Analysis view may have none to show if this is a relatively new dataset (e.g. a weekly set that is less than 7 days old). Percent changes are rounded to the nearest whole number. Changes over time are limited to two decimal places if the answer is below 1, one decimal place if the answer is over 1 but less than 100, and no decimal places if the answer is 100 or more.

Percent change from 0: If the baseline for any comparison would be zero, do not calculate and do not show the calculation. If a dataset starts at 0, it will never show an overall % change, for example. If a dataset dips to 0 and that would be the number to compare to over a "% change over" window, do not calculator or show that statistic.

Run a least squares linear regression on the dataset to get the following numbers. Regression should be over elapsed time, not just the data's position in the dataset.

If a goal value exists and regression indicates it will be reached:
Estimated time to goal: [time in dataset time base e.g. 5 weeks]
(if regression indicates it will not be reached, "Current values suggest goal will not be reached without changes.")

"Estimated value in..." heading, multiple items below may be shown depending on dataset timebase.
 - one day (if time base is minutes)
 - one week (if time base is days or minutes)
 - one month (if time base is weeks, days, or minutes)
 - one year (if time base is months, weeks, days, or minutes)

#### Options menu

 - (blank)

 - (blank)

 - Top

   Scrolls to top



### Correlation Setup

#### Controls

- Back button returns to Correlations view

#### Main content

Name input, must be case-insensitive unique among Correlations.

Instructions: Choose two to four datasets.

Checkbox list of Datum names.

#### Options menu

 - (blank)

 - (blank)

 - Save

   Appears when name is valid and 2-4 datum are checked. Disappears when conditions are not met.



### Correlation

#### Controls

#### Main content

Header, to left:

Correlation name, marquee if too long

Chart with header and range selector, large format, taking up most of screen.

Colored bar in currently selected line color with white text takes up remaining "above the fold" screen. Focus in this view begins on the right-most data point in the chart's first dataset's line, and only the chart points of the selected line are focusable in this view. Colored bar shows data point timestamp to left and value to right.

Chart Y-axis markings are controlled by the selected line, though all lines independently follow their own Y axis whether or not it is visible.

X axis ticks are determined by the range selected.

Goal lines are not considered in Correlation views.

#### Options menu

 - (blank)

 - (blank)

 - Line

   Opens menu with the current Correlation's datum names. Selecting one focuses the last point in its line, changes the colored bar to its color, populates the last point's data into the colored bar, and foregrounds its line over the others.