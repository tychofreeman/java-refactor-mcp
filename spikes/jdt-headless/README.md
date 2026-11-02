# JDT headless spike

Question answered: can JDT Core resolve bindings headless, with no Eclipse
workspace and no OSGi runtime? Answer: yes, for sources on a source path and
for library jars on the classpath. See docs/design.md, open question 1.

Run from the repo root after `mvn -s ~/.m2/settings.xml dependency:build-classpath
-Dmdep.outputFile=/tmp/cp.txt` (the agent sandbox needs the settings file; see
CLAUDE.md):

```sh
java -cp "$(cat /tmp/cp.txt)" spikes/jdt-headless/Spike.java \
     spikes/jdt-headless/sample com/example/App.java
```

Pass extra jars after the unit path to put them on the classpath, for example
`/path/to/slf4j-api.jar` for `sample/com/example/Logged.java`.

`sample/` has a deliberately missing import (`com.missing.Dep`) to show recovery.
