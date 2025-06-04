# OmegaWare Addons (For Meteor Client)

## Features
- **TPA Automations**:
  - Automatically accepts teleport requests from approved users.
  - Can automatically deny teleport requests from unapproved users.
  - Can filter out the servers teleport messages so they are not shown in chat.
  - Has various settings to log when specific actions are taken.
  - **Made for the play.6b6t.org server**.
- **6B6T Chat Filter**:
  - Can filter out specific messages from chat based on the criteria you set.
  - Can filter out messages from specific users.
  - Can filter the chat to only show messages from users with a rank.
- **Beacon Range**:
  - Displays the range of powered beacons.
  - The range box's color can be changed.
  - There is a setting to cull overlapping sections of range boxes.
- **TSR Clan KitBot API**: Disabled for now API not ready
  - Check your token balance.
  - Order kits
  - List your active, pending, completed, and failed orders.
  - Cancel orders.
  - Send tokens to other users.
- **6B6T Item Frame Dupe**
  - Shamelessly taken from https://github.com/kybe236/rusher-auto-item-frame-dupe/
  - With some slight bug fixes and improvements
- **Better Stash Finder**
  - It is practically an exact copy of meteors stash finder
  - Added disconnect on stash found which will also disable auto reconnect if it triggers
  - Changed default values and increased slider maximum values

## Building
### Local Build
1. Clone the repository.
2. Open the terminal and navigate to the cloned repository.
3. Run the following command to build the project:
   ```bash
   ./gradlew build
   ```
4. The built modules will be located in the `build/libs` directory.

### Github Actions Build
1. Fork the repository.
2. Go into the `Actions` tab of the repository.
3. Go to the `Manual Build and Upload` workflow.
4. Click on `Run Workflow`.
5. Once the workflow is complete, the built modules will be located in the `artifacts` section of the workflow run.


## Contributing
Contributions are welcome! If you have any suggestions or improvements, feel free to open an issue or submit a pull request.

## License
[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html) - This project is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.
