# Advent of Code Discord Bot

A simple discord bot written using [Kord](https://github.com/kordlib/kord) to post updates from an Advent of Code private leaderboard.

## Getting Started

After cloning the project, create a `.env` file from the example

```
$ cp .env.example .env
```

and then add the required values.

- `TOKEN`: Your discord bot token
- `YEAR`: The AoC event year to use
- `CODE`: The code of the private leaderboard
- `COOKIE`: Your session cookie for AoC (You can find this in your cookie storage in your browser)
- `CHANNEL`: The ID of the channel to post updates to

Then run with:

```
$ gradlew run
```

