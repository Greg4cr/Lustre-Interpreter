cp ../bin/antlr-4.1-complete.jar ../src/
cp ../bin/antlr-3.4-complete-no-antlrv2.jar ../src/
cp two_counters.greg.lus ../src/
cp inputs.csv ../src/
cp oracle.csv ../src/
mkdir results

cd ../src

echo ""
echo "Compiling"
make compile

echo ""
echo "Performing pre-translation of XOR and equivalence"
java PretranslatePass two_counters.greg.lus >> translated.lus

echo ""
echo "Simulating"
java LustreInterpreter translated.lus inputs.csv simulate >> ../demo/results/trace.csv
cat ../demo/results/trace.csv

echo ""
echo "Tracking OMC/DC Coverage"
java LustreInterpreter translated.lus inputs.csv omcdc "oracle=oracle.csv" >> ../demo/results/omcdc.csv
cat ../demo/results/omcdc.csv
mv matrix.csv ../demo/results/

echo ""
echo "Cleaning"
make clean
rm ordered.lus
rm translated.lus
rm ../src/two_counters.greg.lus
rm ../src/inputs.csv
rm ../src/oracle.csv
rm ../src/antlr-4.1-complete.jar
rm ../src/antlr-3.4-complete-no-antlrv2.jar

cd ../demo/
