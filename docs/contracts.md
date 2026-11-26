# Kontrakty

Kanoniczny interfejs inżynierski stanowią typy `SessionController`, `SessionJournal`, `SessionJournalCodec` i `VariantModuleContract` w projekcie Android. Dziennik SQLite v2 zapisuje zdarzenia, checkpointy i ukończone próby. Kolejka nie ma aktywnego transportu. Reguły badawcze i format danych uczestników nie są zastępowane tym interfejsem.

`contracts/events.schema.json` oraz `examples/event.synthetic.json` pozostają osobnym kontraktem referencyjnym szablonu, nie API eksportu aplikacji. Szczegóły: [persistence](../android/PERSISTENCE.md).
