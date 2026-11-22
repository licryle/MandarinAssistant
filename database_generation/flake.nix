{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        python = pkgs.python3.withPackages (ps: with ps; [
          requests
          python-dotenv
          unidecode
        ]);
      in {
        devShells.default = pkgs.mkShell {
          packages = [
            python
            pkgs.sqlite
            pkgs.sqldiff
            pkgs.sqlitebrowser
          ];
        };
      });
}
