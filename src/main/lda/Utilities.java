package main.lda;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
// For Gamma functions
import org.apache.commons.math3.special.Gamma;

/*
 * This module implements all the distributions, optimizations,
 * and other math utility functions.
 */

public class Utilities {
	// Evaluate the stationary point of a function using Newton-Raphson method,
	// assuming the form of the likelihood function is known (thus, it is not general)
	// In LDA, this is used to maximize the lower bound of log L, (i.e. function is known)
	// wrt \alpha in the M-step
	// Takes in current iteration alpha and gamma values to estimate the value of alpha for the next iteration
	public RealVector performNR(Configs configs, RealVector initAlpha, List<RealVector> gamma){
		
		//Asserting input parameters and their dimensions
		try{
			// Size of initAlpha
			int alphaSize = initAlpha.getDimension();
			// Checking for matching in the dimensions before starting NR iterations
			for(RealVector gammaMember: gamma){
				if(gammaMember.getDimension() != alphaSize)
					throw new IllegalArgumentException("All the members of gamma in NR should have same dimension\n");	
			}
		}
		catch(IllegalArgumentException e){
			e.printStackTrace();
		}
		
		// declarations of running variables
		RealVector gradient, hessianDiag, alphaIncrement;
		double hessianConst;
		
		//New alpha
		RealVector newAlpha = initAlpha;
		// Limiting the number of iterations
		for(int i = 0; i < configs.getMaxNRIterations(); i++){
			// Obtaining the gradient 
			gradient = this.computeGradient(initAlpha, gamma);
			
			// Obtaining the hessian diagonal
			hessianDiag = this.computeHessianDiag(initAlpha, gamma.size());
			
			// Obtaining the hessian constant
			hessianConst = this.computeHessianConstant(initAlpha);
			
			// Obtaining the increment in alpha
			alphaIncrement = this.computeNRStep(hessianDiag, hessianConst, gradient);
			
			// Checking if increment is within the threshold, exist if yes
			if(alphaIncrement.getNorm() < configs.getAlphaChangeThreshold()){
				System.out.println("NR iterations converged after " + i + " steps!");
				break;
			}
			
			// Updating the value of alpha; re-iterate until maximum iterations exhaust or update is small
			newAlpha = newAlpha.subtract(alphaIncrement);
		}
		
		return newAlpha;
	}
	
	/*
	 * Methods specific to Newton Raphson
	 */
	
	// For the given alpha and gamma, computes the gradient to be used for NR iterations
	public RealVector computeGradient(RealVector alpha, List<RealVector> gamma){
		int noDocuments = gamma.size();
		int noTopics = alpha.getDimension();
		
		// Computing some constant terms 
		double alphaElemSum = 0;
		for(int i = 0; i < noTopics; i++)
			alphaElemSum += alpha.getEntry(i);			
		
		double diGammaAlphaSum = this.diGamma(alphaElemSum);
		
		double diGammaDocSum = 0;
		for (int i = 0; i < noDocuments; i++){
			double gammaSum = 0;
			
			for(int j = 0; j < noTopics; j++)
				gammaSum += gamma.get(i).getEntry(j);
			
			diGammaDocSum += this.diGamma(gammaSum);
		}
		
		// Computing the gradient value
		RealVector gradient = new ArrayRealVector(noTopics);
		
		double diGammaDocTerm, gradComponent;
		for(int i = 0 ; i < noTopics; i++){
			diGammaDocTerm = 0;
			for(int j = 0; j < noDocuments; j++){
				diGammaDocTerm += this.diGamma(gamma.get(j).getEntry(i)) - diGammaDocSum;
			}
			
			gradComponent = noDocuments * (diGammaAlphaSum - this.diGamma(alpha.getEntry(i))) + diGammaDocTerm;
			gradient.setEntry(i, gradComponent);
		}
		return gradient;
	}
	
	// For given alpha and gamma, it returns the diagonal of the special form of hessian for NR iterations
	public RealVector computeHessianDiag(RealVector alpha, int noDocuments){
		int noTopics = alpha.getDimension();
		
		// heddian diagonal
		RealVector hessianDiag = new ArrayRealVector(noTopics);  
		for(int i = 0; i < noTopics; i++){
			hessianDiag.setEntry(i, noDocuments * this.triGamma(alpha.getEntry(i)));
		}
		
		return hessianDiag;
	}
	
	//For given alpha and gamma, it returns the constant to run NR iterations
	public double computeHessianConstant(RealVector alpha){
		int noTopics = alpha.getDimension();
		
		// Computing some constant terms 
		double alphaElemSum = 0;
		for(int i = 0; i < noTopics; i++)
			alphaElemSum += alpha.getEntry(i);			
		
		return -1 * this.triGamma(alphaElemSum);
	}
	
	
	// Returns the product of gradient and Hessian for special matrices
	// Form of special hessian : H = diag(h) + \vec(1) * z * \vec(1)t
	// where h is any vector
	// Takes vector h, g and scalar z as inputs
	// Returns the product of Hessian Inverse and gradient for the update in Newton Raphson as one vector
	public RealVector computeNRStep(RealVector hessianDiag, double hessianConst, RealVector gradient){
		
		// (inv(H) * g)_i = (g_i - c) / h_i
		// where c = (sum_{j=1}^k g_j/h_j)/(inv(z) + sum_{j=1}^k inv(h_j))  
		
		double numSum = 0, denSum = 0, constantFactor; //
		int dimensions = hessianDiag.getDimension();
		
		// results
		RealVector product = new ArrayRealVector(dimensions);
		
		//Throwing exception in case the dimensions don't match
		try{
			if(gradient.getDimension() != dimensions)
				throw new IllegalArgumentException("Both gradient and diagonal vector of hessian must be of same dimension\n");
		}
		catch(IllegalArgumentException e){
			e.printStackTrace();
		}
	
		// Computing the numerator and denominator sums
		for (int j = 0; j < dimensions; j++){
			numSum += gradient.getEntry(j) / hessianDiag.getEntry(j);
			denSum += 1/hessianDiag.getEntry(j);
		}
		// Calculating the constant term
		constantFactor = numSum/ (1/hessianConst + denSum);
		
		// Looping to calculate product of inverse of hessian and gradient
		for (int i = 0 ; i < dimensions; i++){
			product.setEntry(i, (gradient.getEntry(i) - constantFactor) / hessianDiag.getEntry(i));
		}
		
		return product;
	}
	
	
	/*
	 * Methods specific to variational inference
	 */
	
	// This method calculates the log gamma function
	public double logGamma(double input){
		return Gamma.logGamma(input);
	}
	
	// This is the taylor approximation of the first derivative of the log gamma function
	public double diGamma(double input){
		return Gamma.digamma(input);
	}
	
	// This is the taylor approximation of the second derivative of the log gamma function
	public double triGamma(double input){
		return Gamma.trigamma(input);
	}
	
	
	/*
	 * Some Useful matrix operations
	 */
	
	// This method returns the column-wise sum of a 2D array double[][]
	public RealVector matSumByCol(RealMatrix input){
		//double[][] data = input.getData();
		return null;
	}
	
	
	/*
	 * Some helper functions for this classs
	 */
	
	// Sum of a vector
	public double vectorSum(RealVector input){
		return 0;
	}
	
}