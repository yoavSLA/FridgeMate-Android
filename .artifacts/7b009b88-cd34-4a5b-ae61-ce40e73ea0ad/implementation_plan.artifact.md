# Redesign Feed Toggle "All/Following"

The goal is to modernize the "All/Following" toggle on the Feed tab to better fit the app's theme and look more professional. The current implementation uses the default outlined `MaterialButtonToggleGroup`, which feels dated.

## Proposed Changes

### [Component] UI Styling - Drawables

#### [NEW] [toggle_background.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/drawable/toggle_background.xml)
A pill-shaped background for the entire toggle group using `@color/accent_green_light`.

#### [NEW] [toggle_button_selector.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/drawable/toggle_button_selector.xml)
A selector for the individual buttons inside the toggle group. It will show a solid `@color/accent_green` background when selected and remain transparent when not.

#### [NEW] [toggle_text_color_selector.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/color/toggle_text_color_selector.xml)
A color selector for the button text: `@color/white` when selected and `@color/accent_green_dark` when unselected.

### [Component] Layout - Feed Fragment

#### [MODIFY] [fragment_feed.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/fragment_feed.xml)
Update the `MaterialButtonToggleGroup` and its `MaterialButton` children to use the new styles. This includes:
- Adding the `toggle_background` to the group.
- Setting `backgroundTint` to `@null` on buttons to allow the selector to work.
- Applying `toggle_button_selector` as the background.
- Applying `toggle_text_color_selector` as the text color.
- Removing borders and adjusting paddings for a "pill" look.

## Verification Plan

### Manual Verification
- Deploy the app to the device.
- Navigate to the Feed tab.
- Verify the new toggle design:
    - It should look like a single pill-shaped container.
    - The selected option should have a green background with white text.
    - The unselected option should have a transparent background with dark green text.
    - Tapping an option should smoothly switch the selection and update the feed content.
