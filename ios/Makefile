# Every iOS task, runnable from VS Code's terminal or its task runner.
#
# Nothing here needs Xcode to be open. `make project` regenerates the
# .xcodeproj from project.yml, which is why that file is in git and the project
# is not.

SIM ?= iPhone 17 Pro
SCHEME = SafeBeauty
PROJECT = SafeBeauty.xcodeproj
DEST = platform=iOS Simulator,name=$(SIM)

.PHONY: help project core-test build test run clean lint

help:
	@echo "make project    regenerate SafeBeauty.xcodeproj from project.yml"
	@echo "make core-test  regenerate Areas.swift, then test the logic package (fast, no simulator)"
	@echo "make build      build the app for the simulator"
	@echo "make test       run the app's test bundle on the simulator"
	@echo "make run        build, install and launch on $(SIM)"
	@echo "make clean      remove build output and the generated project"

project:
	xcodegen generate --spec project.yml

# The fast loop. Most logic lives in SafeBeautyCore precisely so it can be
# checked in a second without a simulator in the way.
core-test: areas
	cd SafeBeautyCore && swift test

# Areas.swift is generated from Android's Areas.kt. Regenerating before the
# tests means the parity test tells you about a real disagreement rather than
# about a generator nobody ran.
.PHONY: areas
areas:
	cd .. && python3 scripts/gen-areas.py

build: project
	xcodebuild -project $(PROJECT) -scheme $(SCHEME) -destination '$(DEST)' \
		-quiet build

test: project
	xcodebuild -project $(PROJECT) -scheme $(SCHEME) -destination '$(DEST)' \
		-quiet test

run: build
	@xcrun simctl boot "$(SIM)" 2>/dev/null || true
	@open -a Simulator
	@APP=$$(xcodebuild -project $(PROJECT) -scheme $(SCHEME) -destination '$(DEST)' \
		-showBuildSettings 2>/dev/null | awk -F' = ' '/ BUILT_PRODUCTS_DIR/{d=$$2} / FULL_PRODUCT_NAME/{n=$$2} END{print d"/"n}'); \
		xcrun simctl install "$(SIM)" "$$APP" && \
		xcrun simctl launch "$(SIM)" com.safebeauty.app

clean:
	rm -rf build $(PROJECT) SafeBeautyCore/.build
