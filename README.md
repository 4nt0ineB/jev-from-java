# Jev from Java

A small web app that calls [Jev](https://docs.typesafe.ai) (TypeSafe's typed decision model) from Java.
There is no Java SDK, so the API contract is typed by hand with records and sealed types. Small wrappers such as `ApiKey`, `ModelId` and `Probability` are the kind of type that becomes a [Valhalla value class](https://openjdk.org/jeps/401) once JEP 401 ships.

![Batch tab: zero-shot intent classification on MASSIVE fr-FR](docs/batch.gif)

Four tabs:

- **Support triage**: a customer message in French or English gets a department, an urgency and risk signals,
  and is routed automatically or to a person depending on confidence.
- **Voice assistant**: 60 MASSIVE intents plus `unsupported`, with typed commands (alarm, weather, lights,
  calendar), in single or multi-action mode.
- **Batch**: drop a CSV, or run the bundled MASSIVE fr-FR sample, and watch each row get classified live.
- **Snake**: a model picks every move of a snake game with one Choice question. Pick the model (Jev, and
  Laya or Open-Jev when they run) and how the position is described, from spelled-out hints to a bare grid.

![Support triage tab](docs/triage.png)

## Run

Requires JDK 25 or newer, Maven, and a TypeSafe API key.

```sh
export TYPESAFE_API_KEY=...

mvn -q package -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" Main
```

Open http://localhost:7070. Tests: `mvn test`; add `JEV_LIVE_TESTS=true` to also run the ones that call the real API.

### With the self-hosted models

`./start.sh` also starts [Laya](https://huggingface.co/convaiinnovations/laya) locally (`laya-serve`) and
[Open-Jev](https://github.com/Zefan-Cai/Open-Jev) 2B on a rented [Modal](https://modal.com) GPU, waits until
both answer, then starts the app. Both speak Jev's wire format, so the same client talks to all three.
It needs Python 3.12 and a Modal account (`modal token new`); `LAYA=0` or `OPENJEV=0` skips one.
Ctrl-C stops everything, including the Modal app.

## License

Code: MIT. The sample in `src/main/resources/samples/` comes from
[MASSIVE](https://github.com/alexa/massive) (Amazon) and is under CC BY 4.0, see its
[NOTICE](src/main/resources/samples/NOTICE.md).
