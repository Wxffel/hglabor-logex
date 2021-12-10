# HGLabor-Log-Extractor

Das Programm durchläuft einmalig deine Minecraft-Logs und sammelt alle Nachrichten heraus, die auf HGLabor geschrieben wurden.

Keine Sorge, deine versauten Privat-Nachrichten (Msg's) werden nicht gesammelt..

### Wie du es benutzen kannst

1. Downloade dir `LogEx-2.1.0.zip` aus dem "Releases"-Tab
2. Entpacke das heruntergeladene Zip-Archiv
3. Öffne den entpackten Ordner `LogEx-2.1.0` und gehe anschließend in den Ordner `bin`
4. Führe nun das Programm `LogEx` aus - auf Windows geht dies z.B. mit einem Doppelklick auf `LogEx.bat`
5. Anschließend beginnt das Programm, mit dem heraus sammeln - der aktuelle Fortschritt wird dir über das Konsolenfenster angezeigt
6. Zuletzt wird eine Datei auf eurem Desktop erstellt, welche die HGLabor Nachrichten enthält - sie heißt `HGLaborMessages_datum`

Abhängig von der Leistung eures PCs und der Menge an Minecraft Logs, kann das sammeln wenige Sekunden bzw. Minuten dauern.
(Ich habe für ca. 2000 Logs ungefähr 2-3 Minuten gebraucht.)

Falls sich das Konsolenfenster **sofort** wieder schließt, dann gab es vermutlich einen unerwarteten Fehler - folge am besten den Schritten für Fortgeschrittene, um den Fehler herauszufinden.

### Für Fortgeschrittene

Ihr könnt das Programm auch direkt von der Konsole ausführen, das hat den Vorteil, dass ihr Argumente und Optionen angeben könnt.
Außerdem werden euch mögliche Fehler angezeigt, ohne das sich die Konsole wieder schließt.

1. Windowstaste drücken, dann `cmd` eingeben und die Konsole (das Programm was bei `cmd` kommt) öffnen
2. In den Ordner gehen, in welchem das Programm enthalten ist (normalerweise der `Downloads`-Ordner)
Gib dafür `cd Downloads\LogEx-2.1.0\bin` in die Konsole ein
3. Jetzt das Programm ausführen `LogEx.bat -h` (oder auf Linux: `./LogEx -h`),
durch den Parameter `-h` wird euch die Hilfe angezeigt, ab hier solltet ihr euch selbst zurechtfinden ;)