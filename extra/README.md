# RootFix UI refresh draft

This folder contains proposed UI replacement files only. Nothing under `app/src/main` has been changed by this draft, and these files are not part of the Gradle source set.

The directory mirrors the app's package paths so the pieces can be reviewed and copied into the live app deliberately:

- `app/src/main/java/com/rootfix/app/ui/theme/Color.kt` proposes a calmer dark teal palette while retaining the color names already used by screens.
- `app/src/main/java/com/rootfix/app/ui/theme/Theme.kt` supplies a drop-in `RootFixTheme` using that palette.
- `app/src/main/java/com/rootfix/app/ui/components/RootFixComponents.kt` contains reusable glass panels, restrained raised buttons, outline buttons, and section labels.

The visual direction combines restrained translucency and edge highlights (glass), soft elevation (neumorphism), and compact spacing and typography (minimalism). Existing screen behavior and repository wiring remain outside this draft.
