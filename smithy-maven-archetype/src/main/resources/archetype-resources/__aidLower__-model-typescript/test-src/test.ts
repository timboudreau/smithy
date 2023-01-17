import {
    expectValid,
    jsonConvertible,
    expectInvalid,
    expectEqual,
    TestSuite,
    TestChain,
    InputWithDescription,
    map,
    MapConsumer,
} from './generated/test-support.js';
import { createTests } from './generated/generated-tests.js';
import * as Process from 'process';
import * as util from 'util';
import * as fs from 'fs';


// Add all of the generated tests to the suite - these test basic aspects
// of the model types, ensuring that fields match constructor arguments,
// validation works correctly, JSON serialization deserializes to an identical
// object, etc.
let suite = createTests(new TestSuite());

/* 
//   You can add your own tests here - this file is only generated by the
//   maven archetype on first invocation.  You can provide a varargs list
//   of InputWithDescription with instances of the type you want to test.
//   They will be passed each input, and can add problems to the problem
//   set if they fail, like this:

 let goodValue: InputWithDescription<MyType> = ["Good MyType", new MyType("Good")];
 suite.add(coll => {
     coll((desc: string, input: MyType, onProblem: (path: string, problem: FailureOutput) => void) => {
         if (input.value !== 'good') {
             onProblem(desc, { "Value should be 'good'": input });
         }
     });
 }, goodValue);
 */


let testFailures = suite.run();
// IF we are passed a file to save the json report to, save it
let outputFile = process.argv[2];
if (testFailures) {
    console.log("Test Failures", util.inspect(testFailures, false, 1000, true));
    if (outputFile) {
        fs.writeFileSync(outputFile, JSON.stringify(testFailures));
    }
    process.exit(1);
} else {
    if (outputFile && fs.existsSync(outputFile)) {
        fs.unlinkSync(outputFile);
    }
}

