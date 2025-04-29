# HBL2 AMU Demo

A Simple AMU using HBL2 as package.

Initialization:

```BASH
git submodule update --init --recursive
```

Chisel Test:
```BASH
make test-top
```

Generate Verilog:
```BASH
make gen-top
```



SIMPLE TEST:

- Init the register
- Store the data back to HBL2
- Load the data from HBL2
- Check the register value
