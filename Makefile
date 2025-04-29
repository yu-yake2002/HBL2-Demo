gen-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_L2L3_AME -td ./build --infer-rw --repl-seq-mem -c:TestTop:-o:TestTop.v.conf

test-top:
	mill -i hbl2demo.test.runMain hbl2demo.TestTop_L2L3_AME_Test -td ./test_run_dir --infer-rw --repl-seq-mem -c:TestTop:-o:TestTop.v.conf

clean:
	rm -rf ./build
	rm -rf ./test_run_dir