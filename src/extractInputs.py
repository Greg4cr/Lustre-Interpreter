# @author Gregory Gay
# 08/06/2013
# Extract inputs for a particular test step from a trace

#!/usr/bin/python

import os
import sys
import copy
import string
import getopt
import re
from math import *

def main(argv):
	#Default values for command line arguments
	oracleFile = ''
	outFile = 'inputs.csv'
	testNum=-1
	stepNum=-1
	inputNum = -1

	try:
      		opts, args = getopt.getopt(argv,"ho:i:t:s:p:")
   	except getopt.GetoptError:
      		print 'extractInputs.py -o <oracle trace file> -i <number of inputs> -t <test number> -s <step number> -p <output file name>'
      		sys.exit(2)
  		
	for opt, arg in opts:
      		if opt == '-h':
			print 'extractInputs.py -o <oracle trace file> -i <number of inputs> -t <test number> -s <step number> -p <output file name>'
			sys.exit()
      		elif opt == '-o':
			if arg == '':
				raise Exception('No oracle trace supplied.')
			else:
         			oracleFile = arg
     		elif opt == '-p':
         		outFile = arg
		elif opt == '-t':
			testNum = int(arg)
		elif opt == '-s': 
			stepNum = int(arg)
		elif opt == "-i":
			inputNum = int(arg)
   	
	if oracleFile == '':
		raise Exception('No oracle trace supplied.')
	if testNum == -1:
		raise Exception('No test number supplied.')
	if inputNum == -1:
		raise Exception('No input number supplied.')


	# Get inputs for the indicated step/test
	f = open(oracleFile)
	currentTest=-1
	currentStep=-1
	line=-1
	inputs=[]
	for l in f:
		line+=1
		parts = l.strip().split(',')
		if line==0:
			inputs.append(parts[:inputNum])
				
		if parts== [''] or line==0:
			currentTest+=1
		elif currentTest==testNum:
			currentStep+=1
			if currentStep==stepNum or stepNum==-1:	
				inputs.append(parts[:inputNum])
	f.close()
 
	f = open(outFile,"w")
	for l in inputs:
		f.write(",".join(l)+"\n")
	f.close()

# Call into main
if __name__ == '__main__':
	main(sys.argv[1:])
