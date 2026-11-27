{
  description = "MandarinAssistant development shell (Maestro + adb)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forEachSystem = f: nixpkgs.lib.genAttrs systems (system: f (import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      }));
    in
    {
      devShells = forEachSystem (pkgs:
        let
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            cmdLineToolsVersion = "13.0";
            platformToolsVersion = "37.0.3";
            buildToolsVersions = [ "37.0.0" ];
            platformVersions = [ "37.0" ];
            abiVersions = [ "x86_64" ];
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis_playstore" ];
          };
          androidSdk = "${androidComposition.androidsdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.maestro
              pkgs.jdk21
              androidComposition.androidsdk

              (pkgs.writeShellScriptBin "tests-maestro" ''
                set -euo pipefail

                root="$(git rev-parse --show-toplevel)"
                cd "$root"

                avd="Medium_Phone_API_37.0"
                sysimg="system-images;android-37;google_apis;x86_64"

                if ! avdmanager list avd -c | grep -qx "$avd"; then
                  echo "tests-maestro: creating AVD $avd"
                  echo "no" | avdmanager create avd \
                    --name "$avd" \
                    --package "$sysimg" \
                    --device "medium_phone"
                fi

                running_emulator() {
                  adb devices | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1; exit }'
                }

                serial="$(running_emulator)"
                if [ -z "$serial" ]; then
                  echo "tests-maestro: starting AVD $avd"
                  nohup emulator -avd "$avd" >/dev/null 2>&1 &
                  for _ in $(seq 1 60); do
                    serial="$(running_emulator)"
                    [ -n "$serial" ] && break
                    sleep 2
                  done
                fi
                if [ -z "$serial" ]; then
                  echo "tests-maestro: no emulator came up (check 'adb devices' and the AVD)" >&2
                  exit 1
                fi
                echo "tests-maestro: using emulator $serial"

                ./gradlew :androidApp:assembleDebug
                ANDROID_SERIAL="$serial" adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

                maestro --device="$serial" test \
                  e2e/setup \
                  e2e/dictionary \
                  e2e/annotations \
                  e2e/wordlists \
                  e2e/widgets \
                  e2e/config \
                  e2e/misc
              '')
            ];

            shellHook = ''
              export JAVA_HOME="${pkgs.jdk21.home}"
              export ANDROID_HOME="${androidSdk}"
              export ANDROID_SDK_ROOT="${androidSdk}"
              export PATH="${androidSdk}/emulator:${androidSdk}/cmdline-tools/13.0/bin:${androidSdk}/platform-tools:$PATH"
            '';
          };
        }
      );
    };
}
