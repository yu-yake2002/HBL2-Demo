gen-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_AME -td ./build --target systemverilog --split-verilog

test-top:
	mill -i hbl2demo.test.test

test-only:
	mill -i hbl2demo.test.testOnly hbl2demo.AMETest

clean:
	rm -rf ./build
	rm -rf ./test_run_dir