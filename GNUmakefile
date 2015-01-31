service=app

LEIN=lein
RM=rm -rf

.PHONY: all test dist clean start stop install uninstall

all:
	$(LEIN) check

test:
	$(LEIN) test

dist:
	$(LEIN) uberjar

publish:
	aws s3 cp target/$(service).jar s3://vaulted/builds/$(service).jar

clean:
	$(LEIN) clean
	$(LEIN) cljsbuild clean

start:
	svc -u /service/$(service)

stop:
	svc -d /service/$(service)

install:
	cp target/$(service).jar /service/$(service)/$(service).jar

uninstall:
	rm -rf /service/$(service)/$(service).jar || true

# eof
