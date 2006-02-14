/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

public class BattleBoard {

    protected final int boardHeight;
    protected final int boardWidth;
    protected final int board[][];
    protected int cityCount;

    /*
     * Java purists would urge the use of a Java 1.5 enum here, but to
     * simplify the text-based protocol, we are using old-fashioned
     * constants.
     *
     * The POS_* constants are used to denote the contents of the
     * board (either known, or inferred).
     */

    /** Indicates an empty (unoccupied, unbombed) board position. */
    public static final int POS_VACANT	= 0;

    /** Indicates a board position that is occupied by a city. */
    public static final int POS_CITY	= 1;

    /** Indicates a board position that has been bombed. */
    public static final int POS_BOMBED	= 2;

    /** Indicates a board position whose contents are unknown. */
    public static final int POS_UNKN	= 3;

    /** Indicates a board position that is near (adjacent) to a city. */
    public static final int POS_NEAR	= 4;

    /** Indicates a board position that is not near (adjacent) to a city. */
    public static final int POS_MISS	= 5;

    /*
     * Return codes from bombBoardPosition.
     */

    /** Indicates the bomb missed any city. */
    static final int MISS       = 100;

    /** Indicates that the bomb landed adjacent to a city. */
    static final int NEAR_MISS  = 101;

    /** Indicates that the bomb hit a city. */
    static final int HIT        = 102;

    /**
     * Creates a BattleBoard with the given width and height and
     * initializes it with cities.  <p>
     *
     * For the sake of simplicity, this constructor always places the
     * cities in the same pattern.  This makes the game very boring to
     * play, but makes the example somewhat simpler.
     *
     * @param width the width of the board
     *
     * @param height the height of the board
     *
     * @param numCities the number of cities to place on the board
     *
     * @throws IllegalArgumentException if the board is an invalid
     * size (either width or height less than 1), if the number of
     * cities is less than one, or if the number of cities is more
     * than will fit onto the board
     */
    public BattleBoard(int width, int height, int numCities) {

	if ((width <= 0) || (height <= 0)) {
	    throw new IllegalArgumentException("width and height must be > 0");
	}

	if (numCities < 1) {
	    throw new IllegalArgumentException("numCities must be > 0");
	}

	if (numCities > (width * height)) {
	    throw new IllegalArgumentException("numCities is too large");
	}

	boardWidth = width;
	boardHeight = height;
	cityCount = numCities;
	board = new int[boardWidth][boardHeight];

	for (int x = 0; x < boardWidth; x++) {
	    for (int y = 0; y < boardHeight; y++) {
		board[x][y] = POS_VACANT;
	    }
	}

	/*
	 * Note that the city locations are chosen in a completely
	 * non-random manner...
	 */

	int count = numCities;
	for (int y = 0; (y < boardHeight) && (count > 0); y++) {
	    for (int x = 0; (x < boardWidth) && (count > 0); x++) {
		board[x][y] = POS_CITY;
		count--;
	    }
	}
    }

    /**
     * Returns the height of the board.
     *
     * @return the height of the board
     */
    public int getHeight() {
	return boardHeight;
    }

    /**
     * Returns the width of the board.
     *
     * @return the width of the board
     */
    public int getWidth() {
	return boardWidth;
    }

    /**
     * Displays the board using a simple text format.
     */
    public void display() {
	for (int j = getHeight() - 1; j >= 0; j--) {

	    System.out.print(j);

	    for (int i = 0; i < getWidth(); i++) {
		String b;

		switch (getBoardPosition(i, j)) {
		    case POS_VACANT : b = "   "; break;
		    case POS_BOMBED : b = " # "; break;
		    case POS_CITY   : b = " C "; break;
		    case POS_UNKN   : b = "   "; break;
		    case POS_NEAR   : b = " + "; break;
		    case POS_MISS   : b = " - "; break;
		    default         : b = "???"; break;
		}
		System.out.print(b);
	    }
	    System.out.println();
	}
	System.out.print(" ");

	for (int i = 0; i < getWidth(); i++) {
	    System.out.print(" " + i + " ");
	}
	System.out.println();
    }

    /**
     * Returns the value at the given (x,y) position on the board.
     *
     * @param x the <em>x</em> coordinate
     *
     * @param y the <em>y</em> coordinate
     *
     * @return the value at the given position in the board
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public int getBoardPosition(int x, int y) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}
	return board[x][y];
    }

    /**
     * Drops a bomb on the given board position (which changes the
     * current contents of the given board position to {@link
     * #POS_BOMBED}, and returns the result.
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return {@link #HIT} if the given position contains a city,
     * {@link #NEAR_MISS} if the given position is adjacent to a city
     * (and not a city itself), or {@link #MISS} if the position is
     * does not contain nor is adjacent to a city
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public int bombBoardPosition(int x, int y) {
	int rc;

	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	if (isHit(x, y)) {
	    rc = HIT;
	    cityCount--;
	} else if (isNearMiss(x, y)) {
	    rc = NEAR_MISS;
	} else {
	    rc = MISS;
	}

	board[x][y] = POS_BOMBED;
	return rc;
    }

    /**
     * Updates the given board position with the given state.  <p>
     *
     * Does not verify that the given state change is actually legal
     * in terms of actual game-play.  For example, it is possible to
     * use this method to change a "near miss" to a "hit" or vice
     * versa.  "Illegal" state changes are permitted in order to allow
     * this method to be used by a player to keep track of their
     * <em>guesses</em> about the contents of the boards of the other
     * players.
     *
     * @param state one of {@link #HIT}, {@link #NEAR_MISS}, or {@link
     * # MISS}.
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public int updateBoardPosition(int x, int y, int state) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	int rc = getBoardPosition(x, y);
	board[x][y] = state;
	return rc;
    }

    /**
     * Indicates whether or not the given board has been "lost".  A
     * board is lost when it contains no cities.
     *
     * @return <code>true</code> if the board contains zero un-bombed
     * cities, <code>false</code> otherwise
     */
    public boolean lost() {
	return (cityCount == 0);
    }

    /**
     * Indicates whether a given position is a hit (currently occupied
     * by a city).
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return <code>true</code> if the given position contains a
     * city, <code>false</code> otherwise
     */
    public boolean isHit(int x, int y) {
	return (getBoardPosition(x, y) == POS_CITY);
    }

    /**
     * Indicates whether a given position is a near miss (not
     * currently occupied by a city, but adjacent to a position that
     * is).
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return <code>true</code> if the given position is a near miss,
     * <code>false</code> otherwise
     */
    public boolean isNearMiss(int x, int y) {

	// Double-check for off-by-one errors!

	int min_x = (x <= 0) ? x : x - 1;
	int min_y = (y <= 0) ? y : y - 1;
	int max_x = (x >= (getWidth() - 1)) ? x : x + 1;
	int max_y = (y >= (getHeight() - 1)) ? y : y + 1;

	if (isHit(x, y)) {
	    return false;
	}

	for (int i = min_x; i <= max_x; i++) {
	    for (int j = min_y; j <= max_y; j++) {
		if ((i == x) && (j == y)) {
		    continue;
		} else if (isHit(i, j)) {
		    return true;
		}
	    }
	}
	return false;
    }
}
