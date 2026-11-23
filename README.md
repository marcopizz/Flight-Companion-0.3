# Flight Companion (v0.3)

App Android per seguire i propri voli: gate e variazioni, storico, mappa, statistiche e recap "passaporto".

## Come compilarla su GitHub
1. Crea un repository GitHub vuoto e carica tutto il contenuto di questa cartella (compresa `.github/`).
2. Vai nella scheda **Actions** → workflow **Build APK** (parte da solo al push, oppure "Run workflow").
3. A build finita, scarica l'artefatto **FlightCompanion-debug-apk**, estrai `app-debug.apk` e installalo sul telefono
   (consenti "installa da origini sconosciute").
4. Se la build fallisce, copia l'errore dal log di Actions: lo correggiamo insieme.

## Configurazione dati (gratis)
- Crea un account gratuito su rapidapi.com, iscriviti al piano Basic di **AeroDataBox** e copia la `X-RapidAPI-Key`.
- Nell'app: icona ingranaggio → incolla la chiave → Salva.
- Meteo: Open-Meteo (senza chiave). Mappa e statistiche: calcolate offline sul telefono.

## Novità della v0.3
- Nuova identità grafica (carta d'imbarco / segnaletica aeroportuale): font Barlow Condensed, Barlow e IBM Plex Mono (licenza OFL), carta calda, blu notte e arancio segnale.
- Globo 3D interattivo (globe.gl + texture Blue Marble, tutto offline): rotazione, zoom, rotte animate, tocco su una rotta per aprire il volo.
- Home: globo a 3/4 dello schermo; l'elenco è un foglio che sale fino a coprire tutto.
- Loghi: 1.958 compagnie (FlightAware e RadarBox da Jxck-S/airline-logos, più bchoubert/airline-logos), tra cui ITA Airways e Wizz Air.
- Sagome degli aerei in pianta per modello (set di forme di tar1090, GPL-2.0+).
- Scheda volo ridisegnata con gerarchia tipografica, tabellone partenza/arrivo, indicatori e grafici; statistiche con grafici ad anello (classe, tipo di posto, motivo).
- Nuovo campo "tipo di posto"; il database ora si aggiorna senza perdere i voli.
- Corretto il crash allo zoom (la mappa piatta non è più zoomabile; l'esplorazione è nel globo).

## Novità della v0.2
- Mappa a schermo intero (tocca l'anteprima): pizzico, trascinamento, doppio tocco; coste e confini dettagliati con lo zoom.
- Passaporto in stile Flighty con bandiere, loghi delle compagnie e ticker animato.
- Schede statistiche aerei e rapporto ritardi (solo i tuoi voli).
- Conto alla rovescia (giorni e ore) nei voli programmati.
- Loghi compagnie (archivio bchoubert/airline-logos, impacchettati in assets/logos.zip, solo uso personale), foto aerei da Planespotters (con credito al fotografo), sagome vettoriali per categoria di aereo.
- Rotta reale da OpenSky Network per i voli degli ultimi 30 giorni (facoltativa: credenziali nelle Impostazioni).
- Nuovo logo dell'app. Il database viene azzerato al primo avvio (nuovo schema): reinserisci i voli.

## Come funziona
- **Aggiunta volo**: numero volo + data → l'app recupera compagnia, aeroporti, orari, gate, terminal.
- **Aggiornamenti**: un worker in background (ogni ~15 min) chiama l'API solo quando serve
  (72h, 24h, 6h, 3h prima, poi ogni 30 min vicino alla partenza, poi in volo e dopo l'atterraggio)
  per risparmiare quota. Notifiche locali su cambio di gate, terminal, orari (≥10 min), stato, nastro bagagli.
- **Archiviazione**: a volo concluso l'app calcola ritardi e durata, recupera età dell'aereo e meteo
  (partenza/arrivo) e sposta il volo in "Passati".
- **Dati manuali**: posto, classe e motivo del viaggio si inseriscono nella scheda del volo.
- **Statistiche**: totale e per anno, con rankings e recap passaporto.

## Limiti noti della v0.1
- Non è stata compilata né provata su dispositivo: possibili errori di build da sistemare al primo giro.
- Turbolenze non incluse (nessuna fonte gratuita affidabile).
- Il database si azzera se cambia lo schema (migrazioni non ancora gestite).
- Il campo `Aggiorna ora` e il polling consumano quota API.
- Nessuna immissione manuale completa di voli non trovati dall'API (da aggiungere se serve).

## Fonti dati incluse
- Contorni continenti: Natural Earth (pubblico dominio). Coordinate aeroporti: OpenFlights (ODbL).
