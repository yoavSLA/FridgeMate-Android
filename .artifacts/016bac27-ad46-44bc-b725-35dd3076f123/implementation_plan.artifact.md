# Add Empty States for Followers and Following

This plan implements stylized empty states for the followers, following, and search screens in the `UserListFragment`, following the design pattern used in the rest of the app (e.g., Fridge and Notifications screens).

## User Review Required

> [!NOTE]
> The empty state icon for followers/following will be `ic_person`, and for search it will be `ic_group`.

## Proposed Changes

### Strings

#### [MODIFY] [strings.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/values/strings.xml)
Add description strings for the empty states.

### Layouts

#### [MODIFY] [fragment_user_list.xml](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/res/layout/fragment_user_list.xml)
Replace the basic `tvEmpty` TextView with a stylized `LinearLayout` container containing an icon, title, and description.

### UI Logic

#### [MODIFY] [UserListFragment.kt](file:///C:/Users/yoavs/StudioProjects/FridgeMate-Android/app/src/main/java/com/project/fridgemate/ui/users/UserListFragment.kt)
Update the logic to show the new empty state container and populate it with mode-specific content.

## Verification Plan

### Manual Verification
1.  Navigate to the "Followers" screen when there are no followers. Verify the new empty state appears with the correct icon and text.
2.  Navigate to the "Following" screen when not following anyone. Verify the new empty state appears.
3.  Search for a non-existent user in the "Discover People" screen. Verify the "No users found" empty state appears.
