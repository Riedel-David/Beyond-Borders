# App-specific R8 rules. Deliberately empty of -keep rules: the audit found nothing
# reflective that the libraries' consumer rules and AAPT's manifest rules do not cover.
#   - Room (generated, instantiated through Room.databaseBuilder), MapLibre, AndroidX
#     and Compose ship their own consumer rules.
#   - No kotlinx.serialization, Gson or Moshi: JSON is read with org.json by key name,
#     never mapped onto classes by field name.
#   - Enums persisted by name (SettingsStore uses valueOf) are safe: an enum constant's
#     name() is a string passed to its constructor, so renaming the field does not change
#     what is stored.
#   - The widget provider, FileProvider and activity are kept via the manifest.
# Add a rule here only when something is reached by name at runtime, with the reason.
