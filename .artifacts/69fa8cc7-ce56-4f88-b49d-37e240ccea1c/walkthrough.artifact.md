# Fridge Tab UI Improvements

I have updated the fridge tab to improve the readability of item names and the visual appeal of the item quantities.

## Changes Made

### 1. Item Name Line Limit
- Modified `item_product.xml` to allow item names to span up to **3 lines** (previously limited to 1).
- Updated the layout to use `layout_weight` and `0dp` width for the name text view, ensuring it wraps correctly when the name is long.

### 2. Amount Display Styling
- Created a new drawable [bg_quantity_badge.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/drawable/bg_quantity_badge.xml) which provides a rounded badge background.
- Updated the quantity `TextView` in [item_product.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/item_product.xml) to use this badge.
- Applied the app's theme colors:
    - **Background**: `@color/accent_green_light` (Light Green)
    - **Text**: `@color/accent_green_dark` (Dark Green)
- Set the quantity text to **bold** and slightly adjusted its size for a more modern, "pill-shaped" look.

## Verification
- Ran `app:assembleDebug` to ensure all resource changes are valid and the project builds successfully.
- Verified that the new drawable correctly references existing theme colors.

render_diffs(file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/item_product.xml)
render_diffs(file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/drawable/bg_quantity_badge.xml)