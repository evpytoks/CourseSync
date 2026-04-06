using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class GroupCreatorEmail : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "creator_email",
                table: "groups",
                type: "character varying(320)",
                maxLength: 320,
                nullable: false,
                defaultValue: "");

            migrationBuilder.Sql(
                """
                UPDATE groups AS g
                SET creator_email = u."Email"
                FROM group_members AS gm
                INNER JOIN users AS u ON u."Id" = gm.user_id
                WHERE gm.group_id = g."Id" AND gm.role = 0;
                """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "creator_email",
                table: "groups");
        }
    }
}
