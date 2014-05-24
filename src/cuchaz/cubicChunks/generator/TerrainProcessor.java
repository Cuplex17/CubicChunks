/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.cubicChunks.generator;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import cuchaz.cubicChunks.generator.biome.biomegen.CubeBiomeGenBase;
import cuchaz.cubicChunks.generator.builder.BasicBuilder;
import cuchaz.cubicChunks.generator.noise.CubeNoiseArray;
import cuchaz.cubicChunks.server.CubeWorldServer;
import cuchaz.cubicChunks.util.Coords;
import cuchaz.cubicChunks.util.CubeProcessor;
import cuchaz.cubicChunks.world.Cube;

public class TerrainProcessor extends CubeProcessor
{
	private CubeWorldServer m_worldServer;
	private CubeBiomeGenBase[] m_biomes;
	
	private Random m_rand;
	private NoiseGeneratorOctaves m_terrainNoiseGenLow;
	private NoiseGeneratorOctaves m_terrainNoiseGenHigh;
	private NoiseGeneratorOctaves m_terrainNoiseGenT;
	private NoiseGeneratorOctaves m_terrainNoiseGenXZ;
	
	private double[] m_terrainNoise;
	private double[] m_terrainNoiseLow;
	private double[] m_terrainNoiseHigh;
	private double[] m_terrainNoiseT;
	private double[] m_terrainNoiseXZ;
	
	private float[] m_filter5x5;
	
	private BasicBuilder builder;
	private int seaLevel;
	private int maxElev;
	
	private CubeNoiseArray cubeNoiseArray;
	
	public TerrainProcessor( String name, CubeWorldServer worldServer, int batchSize )
	{
		super( name, worldServer.getCubeProvider(), batchSize );
		
		m_worldServer = worldServer;
		m_biomes = null;
		
		m_rand = new Random( m_worldServer.getSeed() );
		m_terrainNoiseGenLow = new NoiseGeneratorOctaves( m_rand, 16 );
		m_terrainNoiseGenHigh = new NoiseGeneratorOctaves( m_rand, 16 );
		m_terrainNoiseGenT = new NoiseGeneratorOctaves( m_rand, 8 );
		m_terrainNoiseGenXZ = new NoiseGeneratorOctaves( m_rand, 16 );
		
		m_terrainNoiseLow = null;
		m_terrainNoiseHigh = null;
		m_terrainNoiseT = null;
		m_terrainNoiseXZ = null;
		m_terrainNoise = new double[5*5*3];
		
		cubeNoiseArray = new CubeNoiseArray();
		cubeNoiseArray.setSeed(m_rand.nextInt());
		cubeNoiseArray.build();
		
		seaLevel = 0;
		maxElev = 512;
		
		// init the 5x5 filter
		m_filter5x5 = new float[25];
		for( int i=-2; i<=2; i++ )
		{
			for( int j=-2; j<=2; j++ )
			{
				m_filter5x5[i + 2 + ( j + 2 )*5] = 10.0F/MathHelper.sqrt_float( 0.2F + ( i*i + j*j ) );
			}
		}
		
		builder = new BasicBuilder();
		builder.setSeed(m_rand.nextInt());
		builder.setMaxElev(maxElev);
		builder.setSeaLevel(seaLevel);
		builder.build();
	}
	
	@Override
	public boolean calculate( Cube cube )
	{
		// init random
		m_rand.setSeed( (long)cube.getX()*341873128712L + (long)cube.getZ()*132897987541L );
		
		// get more biome data
		// NOTE: this is different from the column biome data for some reason...
		m_biomes = (CubeBiomeGenBase[])m_worldServer.getCubeWorldProvider().getWorldColumnMananger().getBiomesForGeneration(
			m_biomes,
			cube.getX()*4 - 2, cube.getZ()*4 - 2,
			10, 10
		);
		
		// actually generate the terrain
		generateNoise( cube.getX()*4, cube.getY()*2, cube.getZ()*4 );
		
		generateTerrain( cube );
		
		return true;
	}
	
	private void generateTerrain( Cube cube )
	{
		for( int xRel=0; xRel < 16; xRel++ )
		{
			int xAbs = cube.getX() << 4 | xRel;
			
			for( int zRel=0; zRel < 16; zRel++ )
			{				
				int zAbs = cube.getZ() << 4 | zRel;
				
				for( int yRel=0; yRel < 16; yRel++ )
				{
					int yAbs = cube.getY() << 4 | yRel;
					
					double val = builder.getValue(xAbs, yAbs, zAbs);
					val -= yAbs;
					
					if( val > 0.0D )
					{
						cube.setBlock( xRel, yRel, zRel, Blocks.stone, 0);
					}
					else if( yAbs < seaLevel )
					{
						cube.setBlock( xRel, yRel, zRel, Blocks.water, 0);
					}
					else
					{
						cube.setBlock( xRel, yRel, zRel, Blocks.air, 0);
					}
				}		
			}
		}
		
//		// UNDONE: centralize sea level somehow
//		final int seaLevel = 0;
//		
//		// use the noise to generate the terrain
//		for( int noiseX=0; noiseX<4; noiseX++ )
//		{
//			for( int noiseZ=0; noiseZ<4; noiseZ++ )
//			{
//				for( int noiseY=0; noiseY<2; noiseY++ )
//				{
//					// get the noise samples
//					double noiseXYZ = m_terrainNoise[( noiseX*5 + noiseZ )*3 + noiseY];
//					double noiseXYZp = m_terrainNoise[( noiseX*5 + noiseZ + 1 )*3 + noiseY];
//					double noiseXpYZ = m_terrainNoise[( ( noiseX + 1 )*5 + noiseZ )*3 + noiseY];
//					double noiseXpYZp = m_terrainNoise[( ( noiseX + 1 )*5 + noiseZ + 1 )*3 + noiseY];
//					
//					double noiseXYpZ = m_terrainNoise[( noiseX*5 + noiseZ )*3 + noiseY + 1];
//					double noiseXYpZp = m_terrainNoise[( noiseX*5 + noiseZ + 1 )*3 + noiseY + 1];
//					double noiseXpYpZ = m_terrainNoise[( ( noiseX + 1 )*5 + noiseZ )*3 + noiseY + 1];
//					double noiseXpYpZp = m_terrainNoise[( ( noiseX + 1 )*5 + noiseZ + 1 )*3 + noiseY + 1];
//					
//					// interpolate the noise linearly in the y dimension
//					double yStepXZ = ( noiseXYpZ - noiseXYZ )/8;
//					double yStepXZp = ( noiseXYpZp - noiseXYZp )/8;
//					double yStepXpZ = ( noiseXpYpZ - noiseXpYZ )/8;
//					double yStepXpZp = ( noiseXpYpZp - noiseXpYZp )/8;
//					
//					for( int y=0; y<8; y++ )
//					{
//						int localY = noiseY*8 + y;
//						int blockY = Coords.localToBlock( cube.getY(), localY );
//						
//						// interpolate noise linearly in the x dimension
//						double valXYZ = noiseXYZ;
//						double valXYZp = noiseXYZp;
//						double xStepYZ = ( noiseXpYZ - noiseXYZ )/4;
//						double xStepYZp = ( noiseXpYZp - noiseXYZp )/4;
//						
//						for( int x=0; x<4; x++ )
//						{
//							int localX = noiseX*4 + x;
//							
//							// interpolate noise linearly in the z dimension
//							double zStepXY = ( valXYZp - valXYZ )/4;
//							double val = zStepXY;
//							
//							for( int z=0; z<4; z++ )
//							{
//								int localZ = noiseZ*4 + z;
//								
//								if(blockY == 63)
//								{
//									System.out.println(localX + ":" + localY + ":" + localZ + ":" + val);
//								}
//								
//								if( val - blockY > 0.0D )
//								{
//									cube.setBlockForGeneration( localX, localY, localZ, Blocks.stone, 0 );
//								}
//								else if( blockY < seaLevel )
//								{
//									cube.setBlockForGeneration( localX, localY, localZ, Blocks.water, 0 );
//								}
//								
//								// one step in the z dimension
//								val += zStepXY;
//							}
//							
//							// one step in the x dimension
//							valXYZ += xStepYZ;
//							valXYZp += xStepYZp;
//						}
//						
//						// one step in the y dimension
//						noiseXYZ += yStepXZ;
//						noiseXYZp += yStepXZp;
//						noiseXpYZ += yStepXpZ;
//						noiseXpYZp += yStepXpZp;
//					}
//				}
//			}
//		}
	}
	
	private void generateNoise( int noiseX, int noiseY, int noiseZ )
	{
		// NOTE: the noise coordinate system samples world space differently in each dimension
		//   x,z: once every 4 blocks
		//   y:   once every 8 blocks
		// this means each cube uses 5 samples in x, 5 samples in z, and 3 samples in y
		final double noiseScale = 684.412;
		
//		m_terrainNoiseLow = m_terrainNoiseGenLow.generateNoiseOctaves( m_terrainNoiseLow,
//			noiseX, noiseY, noiseZ,            // offset
//			5, 3, 5,                           // size
//			noiseScale, noiseScale, noiseScale // scale
//		);
//		m_terrainNoiseHigh = m_terrainNoiseGenHigh.generateNoiseOctaves( m_terrainNoiseHigh,
//			noiseX, noiseY, noiseZ,
//			5, 3, 5,
//			noiseScale, noiseScale, noiseScale
//		);
//		m_terrainNoiseT = m_terrainNoiseGenT.generateNoiseOctaves( m_terrainNoiseT,
//			noiseX, noiseY, noiseZ,
//			5, 3, 5,
//			noiseScale/80, noiseScale/160, noiseScale/80
//		);
//		m_terrainNoiseXZ = m_terrainNoiseGenXZ.generateNoiseOctaves( m_terrainNoiseXZ,
//			noiseX, 10, noiseZ,
//			5, 1, 5,
//			200, 1, 200
//		);
		
		m_terrainNoiseLow = cubeNoiseArray.getCubeNoiseArray(noiseX, noiseY, noiseZ);
		
		int noiseIndex = 0;
		
		for( int x=0; x<=4; x++ )
		{
			for( int z=0; z<=4; z++ )
			{
				CubeBiomeGenBase biome = m_biomes[x + 2 + ( z + 2 )*10];
				
				// compute biome height info
				float maxHeightConditioned = 0;
				float minHeightConditioned = 0;
				float sumHeightScale = 0;
				
				// apply 5x5 filter to smooth the height scale based on the surrounding biome values. This actually reaches 8 blocks in each direction and smooths.
				for( int i=-2; i<=2; i++ )
				{
					for( int j=-2; j<=2; j++ )
					{
						CubeBiomeGenBase ijBiome = m_biomes[x + i + 2 + ( z + j + 2 )*10]; // retrieve the biomes in a 5x5 (16x16) square around the x,z coord
						
						// ranges from -1.0 to 1.0
						float minHeight = ijBiome.minHeight; // min biome height at the x + i, z + j coord
						float maxHeight = ijBiome.maxHeight; // max biome height at the x + i, z + j coord.
						
						// if world type is amplified, increase the height a lot
						if( m_worldServer.getWorldInfo().getTerrainType() == WorldType.field_151360_e && minHeight > 0.0F )
						{
							minHeight = minHeight*2 + 1;
							maxHeight = maxHeight*4 + 1;
						}
						
						// compute the height scale
						// divide the filter value by the minheight for the biome + 2
						float heightScale = m_filter5x5[i + 2 + ( j + 2 )*5]/( minHeight + 2.0F ); // get the  scale factor at i,j
						
						if( ijBiome.minHeight > biome.minHeight ) // if the biome minheight at x + i, z + j coord is greater than the biome minheight at x, z, cut the scale in half to smooth
						{
							heightScale /= 2;
						}
						
						maxHeightConditioned += maxHeight * heightScale; // smooth between the min heights and add
						minHeightConditioned += minHeight * heightScale; // smooth between the max heights and add
						sumHeightScale += heightScale; // add the height scale from each iteration into a total
					}
				}
				
//				maxHeightConditioned /= sumHeightScale;
//				minHeightConditioned /= sumHeightScale;
//				
//				maxHeightConditioned = maxHeightConditioned * 0.9F + 0.1F;
//				minHeightConditioned = ( minHeightConditioned * 4.0F - 1.0F ) / 8.0F;
//				
//				// get and condition XZ noise
//				double noiseXZ = m_terrainNoiseXZ[noiseIndexXZ++]/8000;
//				if( noiseXZ < 0 )
//				{
//					noiseXZ = -noiseXZ * 0.3;
//				}
//				noiseXZ = noiseXZ * 3 - 2;
//				if( noiseXZ < 0 )
//				{
//					noiseXZ /= 2;
//					
//					if( noiseXZ < -1 )
//					{
//						noiseXZ = -1;
//					}
//					
//					noiseXZ /= 1.4;
//					noiseXZ /= 2;
//				}
//				else
//				{
//					if( noiseXZ > 1 )
//					{
//						noiseXZ = 1;
//					}
//					
//					noiseXZ /= 8;
//				}
//				noiseXZ *= 0.2*8.5/8;
//				
//				// handle the y dimension
//				double yInfo = 8.5 + ( (double)minHeightConditioned + noiseXZ )*4;
				
				for( int y=0; y<=2; y++ )
				{
//					// compute the noise offset
//					double noiseOffset = ( (double)( y + noiseY ) - yInfo ) * 12.0D * 128.0D / 256.0D / maxHeightConditioned;
//					if( noiseOffset < 0.0D )
//					{
//						noiseOffset *= 4.0D;
//					}
//					
//					double lowNoise = m_terrainNoiseLow[noiseIndex] / 512.0D;
//					double highNoise = m_terrainNoiseHigh[noiseIndex] / 512.0D;
//					double tNoise = ( m_terrainNoiseT[noiseIndex] / 10.0D + 1.0D ) / 2.0D;
//					m_terrainNoise[noiseIndex++] = MathHelper.denormalizeClamp( lowNoise, highNoise, tNoise ) - noiseOffset;
					double value = m_terrainNoiseLow[noiseIndex] * (sumHeightScale / 154.214);
					m_terrainNoise[noiseIndex++] = value;
				}
			}
		}
		
//		for( int x=0; x<=4; x++ )
//		{
//			for( int z=0; z<=4; z++ )
//			{
//				CubeBiomeGenBase biome = m_biomes[x + 2 + ( z + 2 )*10];
//				
//				// compute biome height info
//				float maxHeightConditioned = 0;
//				float minHeightConditioned = 0;
//				float sumHeightScale = 0;
//				
//				// apply 5x5 filter to smooth the height scale based on the surrounding biome values. This actually reaches 8 blocks in each direction and smooths.
//				for( int i=-2; i<=2; i++ )
//				{
//					for( int j=-2; j<=2; j++ )
//					{
//						CubeBiomeGenBase ijBiome = m_biomes[x + i + 2 + ( z + j + 2 )*10]; // retrieve the biomes in a 5x5 (16x16) square around the x,z coord
//						
//						float minHeight = ijBiome.minHeight; // min biome height at the x + i, z + j coord
//						float maxHeight = ijBiome.maxHeight; // max biome height at the x + i, z + j coord.
//						
//						// if world type is amplified, increase the height a lot
//						if( m_worldServer.getWorldInfo().getTerrainType() == WorldType.field_151360_e && minHeight > 0.0F )
//						{
//							minHeight = minHeight*2 + 1;
//							maxHeight = maxHeight*4 + 1;
//						}
//						
//						// compute the height scale
//						float heightScale = m_filter5x5[i + 2 + ( j + 2 )*5]/( minHeight + 2.0F ); // get the  scale factor at i,j
//						
//						if( ijBiome.minHeight > biome.minHeight ) // if the biome minheight at x + i, z + j coord is greater than the biome minheight at x, z, cut the scale in half to smooth
//						{
//							heightScale /= 2;
//						}
//						
//						maxHeightConditioned += maxHeight * heightScale; // smooth between the min heights and add
//						minHeightConditioned += minHeight * heightScale; // smooth between the max heights and add
//						sumHeightScale += heightScale; // add the height scale from each iteration into a total
//					}
//				}
//				
//				maxHeightConditioned /= sumHeightScale;
//				minHeightConditioned /= sumHeightScale;
//				
//				maxHeightConditioned = maxHeightConditioned * 0.9F + 0.1F;
//				minHeightConditioned = ( minHeightConditioned * 4.0F - 1.0F ) / 8.0F;
//				
//				// get and condition XZ noise
//				double noiseXZ = m_terrainNoiseXZ[noiseIndexXZ++]/8000;
//				if( noiseXZ < 0 )
//				{
//					noiseXZ = -noiseXZ * 0.3;
//				}
//				noiseXZ = noiseXZ * 3 - 2;
//				if( noiseXZ < 0 )
//				{
//					noiseXZ /= 2;
//					
//					if( noiseXZ < -1 )
//					{
//						noiseXZ = -1;
//					}
//					
//					noiseXZ /= 1.4;
//					noiseXZ /= 2;
//				}
//				else
//				{
//					if( noiseXZ > 1 )
//					{
//						noiseXZ = 1;
//					}
//					
//					noiseXZ /= 8;
//				}
//				noiseXZ *= 0.2*8.5/8;
//				
//				// handle the y dimension
//				double yInfo = 8.5 + ( (double)minHeightConditioned + noiseXZ )*4;
//				
//				for( int y=0; y<=2; y++ )
//				{
//					// compute the noise offset
//					double noiseOffset = ( (double)( y + noiseY ) - yInfo ) * 12.0D * 128.0D / 256.0D / maxHeightConditioned;
//					if( noiseOffset < 0.0D )
//					{
//						noiseOffset *= 4.0D;
//					}
//					
//					double lowNoise = m_terrainNoiseLow[noiseIndex] / 512.0D;
//					double highNoise = m_terrainNoiseHigh[noiseIndex] / 512.0D;
//					double tNoise = ( m_terrainNoiseT[noiseIndex] / 10.0D + 1.0D ) / 2.0D;
//					m_terrainNoise[noiseIndex++] = MathHelper.denormalizeClamp( lowNoise, highNoise, tNoise ) - noiseOffset;
//				}
//			}
//		}
	}
}
