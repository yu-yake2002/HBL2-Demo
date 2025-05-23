gen-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_L2L3_AME -td ./build --target systemverilog --split-verilog

test-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_L2L3_AME_Test -td ./test_run_dir --target systemverilog --split-verilog

clean:
	rm -rf ./build
	rm -rf ./test_run_dir