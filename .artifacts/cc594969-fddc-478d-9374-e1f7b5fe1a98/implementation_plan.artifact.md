# Force Light Mode and Clean Up Card Borders

The user reported UI discrepancies on physical devices (Pixel 8 Pro XL and Xiaomi 14 Ultra) compared to the emulator. These issues (pale text on white backgrounds and unexpected card borders) are caused by the devices running in **Dark Mode** or using **Material 3 Dynamic Colors** while the app's layouts use hardcoded white backgrounds.

Since the user wants the app to look consistent with the emulator, we will force **Light Mode** globally and ensure Material 3 cards do not show unexpected borders.

## Proposed Changes

### [Theme Configuration]

#### [MODIFY] [themes.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/values/themes.xml)
- Change parent theme from `Theme.Material3.DayNight.NoActionBar` to `Theme.Material3.Light.NoActionBar` to disable automatic dark mode switching.
- Explicitly set the default `materialCardViewStyle` to `Widget.Material3.CardView.Elevated` to ensure cards use elevation instead of outlines (borders) by default.

### [UI Components]

#### [MODIFY] [item_post.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/item_post.xml)
- Add `app:strokeWidth="0dp"` to the root `MaterialCardView` to guarantee no border is rendered, regardless of system defaults.

#### [MODIFY] [fragment_feed.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/fragment_feed.xml)
- (Already has `app:strokeWidth="0dp"` for some cards, but we'll ensure consistency if needed. Checked: the empty state card already has it.)

## Verification Plan

### Manual Verification
1. Deploy the app to the physical devices (Pixel 8 Pro XL / Xiaomi 14 Ultra).
2. Ensure the devices are set to **Dark Mode** in system settings.
3. Verify that the app remains in **Light Mode** (white backgrounds, black text).
4. Verify that the post cards in the feed:
    - Have clearly visible black text for names and titles.
    - Do not have a black border.
    - Still have the intended elevation/shadow.
