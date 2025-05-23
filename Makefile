gen-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_L2L3_AME -td ./build --target systemverilog --split-verilog

test-top:
	mill -i hbl2demo.test.test

clean:
	rm -rf ./build
	rm -rf ./test_run_dir